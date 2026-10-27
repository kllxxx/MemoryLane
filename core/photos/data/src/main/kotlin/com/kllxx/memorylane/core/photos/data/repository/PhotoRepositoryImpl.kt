package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.photos.data.PhotosConstants
import com.kllxx.memorylane.core.photos.data.di.IoDispatcher
import com.kllxx.memorylane.core.photos.data.date.CacheLookup
import com.kllxx.memorylane.core.photos.data.date.ExifDateReader
import com.kllxx.memorylane.core.photos.data.date.PhotoDateResolver
import com.kllxx.memorylane.core.photos.data.date.ResolvedDate
import com.kllxx.memorylane.core.photos.data.date.UndatedPhoto
import com.kllxx.memorylane.core.photos.data.date.UndatedPhotoDates
import com.kllxx.memorylane.core.photos.data.geocoder.LocationGeocoder
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DayCover
import com.kllxx.memorylane.core.photos.domain.model.DayMemories
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoDateCache
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PhotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geocoder: LocationGeocoder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val exifReader: ExifDateReader,
    private val dateCache: PhotoDateCache,
) : PhotoRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    private val dateResolver = PhotoDateResolver(exifReader, clock)
    private val undatedPhotoDates = UndatedPhotoDates(dateCache, dateResolver)

    // Reading the files of undated photos goes on after the index was shown, so it is not tied to the caller
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile
    private var resolutionJob: Job? = null

    // The first scan of this process is started by the first getPhotosForDate if nothing else has by then
    private val indexLoadStarted = AtomicBoolean(false)

    override fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private val refreshLock = Any()
    private val _onThisDayIndex = MutableStateFlow<OnThisDayIndex?>(null)
    override val onThisDayIndex: StateFlow<OnThisDayIndex?> = _onThisDayIndex.asStateFlow()

    // A refresh that starts while another one runs waits for it instead of querying again
    private var refreshInFlight: CompletableDeferred<Unit>? = null

    override suspend fun refreshOnThisDayIndex() {
        // Without the permission MediaStore returns an empty cursor, which must not look like an empty gallery
        if (!hasMediaPermission()) throw PermissionDeniedException("Media read permission is not granted")
        var owner = false
        val run = synchronized(refreshLock) {
            refreshInFlight ?: CompletableDeferred<Unit>().also {
                refreshInFlight = it
                owner = true
            }
        }
        if (!owner) {
            run.await()
            return
        }
        try {
            val scan = withContext(ioDispatcher) { scanMediaStore() }
            // What the cache knows about the undated photos is part of the first index: publishing the MediaStore dates
            // alone would drop those photos for a moment and bring them back with the next one
            val lookup = withContext(ioDispatcher) { undatedPhotoDates.lookup(scan.undated) }
            lookup.dates.forEach { (id, date) -> scan.builder.add(IndexedImage(id, date.toInstant(scan.zone).toEpochMilli())) }
            _onThisDayIndex.value = scan.toIndex()
            // Only the files the cache has no date for are read, after this index was shown; the index is emitted
            // again, once, if that found anything
            resolutionJob?.cancel()
            resolutionJob = scope.launch { resolveUndatedDates(scan, lookup) }
            run.complete(Unit)
        } catch (e: Throwable) {
            run.completeExceptionally(e)
            throw e
        } finally {
            synchronized(refreshLock) { refreshInFlight = null }
        }
    }

    /** What one pass over the gallery found: the dated images already counted, and the photos that still need a date. */
    private class MediaScan(
        val builder: OnThisDayIndexBuilder,
        val undated: List<UndatedPhoto>,
        val currentYear: Int,
        val zone: ZoneId,
    ) {
        fun toIndex(): OnThisDayIndex = OnThisDayIndex(
            currentYear = currentYear,
            entries = builder.entries().mapValues { (_, value) ->
                val (count, cover) = value
                DayMemories(
                    count = count,
                    cover = DayCover(cover.id, ContentUris.withAppendedId(COLLECTION_URI, cover.id), cover.takenOn),
                )
            },
        )
    }

    /**
     * One query over every image of the gallery: no filter on type, folder or date. Images with a `DATE_TAKEN` go
     * straight into the index; the others are only listed, nothing opens their files here.
     */
    private fun scanMediaStore(): MediaScan {
        val diagnostics = PerfLog.isEnabled()
        val startedAt = PerfLog.nanoTime()
        val zone = ZoneId.systemDefault()
        val currentYear = LocalDate.now(clock).year
        val builder = OnThisDayIndexBuilder(currentYear, PhotosConstants.MAX_YEARS_BACK, zone)
        val undated = ArrayList<UndatedPhoto>()
        val stats = IndexLoadStats()

        val projection = buildList {
            addAll(INDEX_PROJECTION)
            if (diagnostics) {
                add(MediaStore.Images.Media.MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()
        val cursor = resolver.query(COLLECTION_URI, projection, null, null, null)
        val queryMs = PerfLog.sinceMs(startedAt)
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modifiedColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                -1
            }
            while (it.moveToNext()) {
                stats.rows++
                val id = it.getLong(idColumn)
                val taken = if (it.isNull(takenColumn)) null else it.getLong(takenColumn)
                if (taken != null && taken > 0) {
                    builder.add(IndexedImage(id, taken))
                } else {
                    stats.dateTakenMissing++
                    undated += UndatedPhoto(
                        id = id,
                        dateModified = if (it.isNull(modifiedColumn)) 0L else it.getLong(modifiedColumn),
                        name = it.getString(nameColumn),
                        mimeType = if (mimeColumn >= 0) it.getString(mimeColumn) else null,
                    )
                }
                if (diagnostics) {
                    if (mimeColumn >= 0) stats.byMime.merge(it.getString(mimeColumn) ?: "null", 1, Int::plus)
                    if (pathColumn >= 0) stats.byPath.merge(it.getString(pathColumn) ?: "null", 1, Int::plus)
                }
            }
        }

        val scan = MediaScan(builder, undated, currentYear, zone)
        PerfLog.d {
            "index: query=${queryMs}ms total=${PerfLog.sinceMs(startedAt)}ms rows=${stats.rows} " +
                "dateTakenNullOrZero=${stats.dateTakenMissing} inWindow=${builder.counted} " +
                "entries=${builder.entries().size} years=${currentYear - PhotosConstants.MAX_YEARS_BACK}..${currentYear - 1}"
        }
        PerfLog.d { "index: by mime type ${stats.byMime.toSortedMap()}" }
        PerfLog.d { "index: by relative path ${stats.byPath.toSortedMap()}" }
        PerfLog.d { "index: permissions ${permissionStates()}" }
        return scan
    }

    /**
     * Dates the photos the cache has no date for, from their EXIF data or file name, adds them to the index of [scan]
     * and emits it again, once, when that changed anything. Photos with no date at all stay out.
     */
    private suspend fun resolveUndatedDates(scan: MediaScan, lookup: CacheLookup) {
        val startedAt = PerfLog.nanoTime()
        try {
            if (PerfLog.isEnabled()) logExifSamples(scan.undated)
            val resolution = undatedPhotoDates.resolveMisses(lookup.misses)
            currentCoroutineContext().ensureActive()
            val changed: Boolean
            if (lookup.changedBy(resolution)) {
                // A file that was changed since it was cached has another date than the one the index was built with:
                // the index is built again, now from a cache that is up to date (the only case with a second query)
                val rebuilt = withContext(ioDispatcher) {
                    scanMediaStore().also { again ->
                        undatedPhotoDates.lookup(again.undated).dates.forEach { (id, date) ->
                            again.builder.add(IndexedImage(id, date.toInstant(again.zone).toEpochMilli()))
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                _onThisDayIndex.value = rebuilt.toIndex()
                changed = true
            } else {
                val countedBefore = scan.builder.counted
                // A stale photo is in the index already, with the date the file still has
                resolution.dates.filterKeys { it !in lookup.dates }.forEach { (id, date) ->
                    scan.builder.add(IndexedImage(id, date.toInstant(scan.zone).toEpochMilli()))
                }
                changed = scan.builder.counted != countedBefore
                if (changed) _onThisDayIndex.value = scan.toIndex()
            }
            PerfLog.d {
                "dates: undated=${lookup.undated} phase1CacheHits=${lookup.cacheHits} misses=${lookup.misses.size} " +
                    "stale=${lookup.stale.size} exif=${resolution.fromExif} fileName=${resolution.fromFileName} " +
                    "stillUndated=${resolution.stillUndated} indexChanged=$changed took=${PerfLog.sinceMs(startedAt)}ms"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The index stays as it was; the next refresh tries again with whatever reached the cache
            Log.e(TAG, "Could not date the photos without a MediaStore date", e)
        }
    }

    /** For up to [DIAGNOSTIC_SAMPLES] undated photos: the raw EXIF tags, and what the chain makes of them. */
    private fun logExifSamples(undated: List<UndatedPhoto>) {
        undated.take(DIAGNOSTIC_SAMPLES).forEach { photo ->
            val tags = try {
                exifReader.read(photo.id).describe()
            } catch (e: Exception) {
                "unreadable: $e"
            }
            val resolved = dateResolver.resolve(photo)
            PerfLog.d {
                "dates: sample id=${photo.id} name=${photo.name} mime=${photo.mimeType} modified=${photo.dateModified}s " +
                    "exif[$tags] resolved=${resolved?.let { "${it.dateTime} offset=${it.offset} via ${it.source}" }}"
            }
        }
    }

    /** The raw state of every photo permission that applies to this Android version, to tell full from partial access. */
    private fun permissionStates(): String {
        fun state(permission: String) =
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
        return buildString {
            append("sdk=${Build.VERSION.SDK_INT}")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    append(" READ_MEDIA_IMAGES=${state(Manifest.permission.READ_MEDIA_IMAGES)}")
                    append(" READ_MEDIA_VISUAL_USER_SELECTED=${state(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)}")
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    append(" READ_MEDIA_IMAGES=${state(Manifest.permission.READ_MEDIA_IMAGES)}")
                else -> append(" READ_EXTERNAL_STORAGE=${state(Manifest.permission.READ_EXTERNAL_STORAGE)}")
            }
        }
    }

    private class IndexLoadStats {
        var rows = 0
        var dateTakenMissing = 0
        val byMime = HashMap<String, Int>()
        val byPath = HashMap<String, Int>()
    }

    /**
     * The photos taken on this month/day in earlier years: those with a MediaStore `DATE_TAKEN` from the query, plus the
     * undated ones whose date the cache already holds (EXIF, file name) and falls on the same day, so a photo is on the
     * same day here and in the calendar index. Photos with no date at all are in neither.
     *
     * It never waits for the files to be read: a photo that is not in the cache yet is left out and shows up the next
     * time this is called, once the background pass has cached it.
     */
    override suspend fun getPhotosForDate(date: LocalDate): List<Photo> = withContext(ioDispatcher) {
        // Without the permission MediaStore returns an empty cursor, which must not look like an empty day
        if (!hasMediaPermission()) throw PermissionDeniedException("Media read permission is not granted")
        ensureIndexLoading()
        val zone = ZoneId.systemDefault()
        val startedAt = PerfLog.nanoTime()
        val cursor = queryOnThisDay(date, zone)
        val queryMs = PerfLog.sinceMs(startedAt)
        val dated = cursor?.use {
            buildList {
                while (it.moveToNext()) add(it.toPhoto(zone))
            }
        }.orEmpty()
        val fromCache = photosFromCache(date, zone)
        // Stable, so photos of the same day keep the order of the query
        val photos = (dated + fromCache).sortedBy { it.dateTaken }
        PerfLog.d {
            "mediastore getPhotosForDate date=$date query=${queryMs}ms total=${PerfLog.sinceMs(startedAt)}ms " +
                "rows=${photos.size} fromCache=${fromCache.size}"
        }
        photos
    }

    /**
     * Starts the first scan of this process in the background when nothing has loaded the index yet, so that the files
     * of undated photos get read and cached even if the user never opens Calendar. Not awaited, and a failure is
     * only logged: the caller has its answer from MediaStore and the cache either way.
     */
    private fun ensureIndexLoading() {
        if (_onThisDayIndex.value != null || !indexLoadStarted.compareAndSet(false, true)) return
        scope.launch {
            try {
                refreshOnThisDayIndex()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the photo index in the background", e)
            }
        }
    }

    /** The undated photos whose cached date is this month/day in the look-back window, as long as their file is unchanged. */
    private suspend fun photosFromCache(date: LocalDate, zone: ZoneId): List<Photo> {
        val monthDay = MonthDay.from(date)
        val years = (date.year - PhotosConstants.MAX_YEARS_BACK)..(date.year - 1)
        val onThisDay = HashMap<Long, Pair<LocalDate, Long>>()
        for (entry in dateCache.getAllDated()) {
            val day = ResolvedDate.fromCached(entry)?.toLocalDate(zone) ?: continue
            if (day.year in years && MonthDay.from(day) == monthDay) onThisDay[entry.id] = day to entry.dateModified
        }
        if (onThisDay.isEmpty()) return emptyList()

        return onThisDay.keys.chunked(SQL_VARIABLE_LIMIT).flatMap { ids ->
            val selection = "${MediaStore.Images.Media._ID} IN (${ids.joinToString(",") { "?" }})"
            val args = ids.map { it.toString() }.toTypedArray()
            resolver.query(COLLECTION_URI, CACHE_PROJECTION, selection, args, null)?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val takenColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val modifiedColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                buildList {
                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        // The cached id is the key: a query answering with other rows must not add photos
                        val (day, cachedModified) = onThisDay[id] ?: continue
                        // MediaStore has a date for it now, or the file changed: the cache entry no longer applies
                        val hasDateTaken = !it.isNull(takenColumn) && it.getLong(takenColumn) > 0
                        val modified = if (it.isNull(modifiedColumn)) 0L else it.getLong(modifiedColumn)
                        if (hasDateTaken || modified != cachedModified) continue
                        add(
                            Photo(
                                id = id,
                                uri = ContentUris.withAppendedId(COLLECTION_URI, id),
                                dateTaken = day,
                                sizeBytes = it.getLong(sizeColumn),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }
    }

    override suspend fun getPhotoLocation(photoId: Long): String? = withContext(ioDispatcher) {
        try {
            val (latitude, longitude) = coordinatesOf(ContentUris.withAppendedId(COLLECTION_URI, photoId))
                ?: return@withContext null
            geocoder.getLocation(latitude, longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A location is a nice-to-have, so any failure just means there is none
            Log.e(TAG, "Could not look up the location of photo $photoId", e)
            null
        }
    }

    /**
     * On API 30+ the user must confirm via the returned [DeleteResult.RequiresConfirmation].
     * Below 30 photos are deleted directly; a [SecurityException] is thrown for photos
     * the app does not own.
     */
    override suspend fun deletePhotos(photoIds: List<Long>): DeleteResult =
        withContext(ioDispatcher) {
            if (photoIds.isEmpty()) return@withContext DeleteResult.Deleted
            val uris = photoIds.map { ContentUris.withAppendedId(COLLECTION_URI, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(resolver, uris)
                DeleteResult.RequiresConfirmation(request.intentSender)
            } else {
                uris.forEach { resolver.delete(it, null, null) }
                DeleteResult.Deleted
            }
        }

    /**
     * API 29+ redacts GPS data in MediaStore, so it is read from the original file's EXIF (needs
     * ACCESS_MEDIA_LOCATION). Below that the deprecated MediaStore columns still hold it.
     */
    private fun coordinatesOf(uri: Uri): Pair<Double, Double>? {
        val coordinates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) readExifCoordinates(uri) else null
        } else {
            queryLegacyCoordinates(uri)
        }
        // 0/0 is how missing GPS data is commonly stored
        return coordinates?.takeUnless {
            it.first == LocationGeocoder.INVALID_COORDINATE && it.second == LocationGeocoder.INVALID_COORDINATE
        }
    }

    private fun queryLegacyCoordinates(uri: Uri): Pair<Double, Double>? =
        resolver.query(uri, arrayOf(LATITUDE_COLUMN, LONGITUDE_COLUMN), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0) && !it.isNull(1)) it.getDouble(0) to it.getDouble(1) else null
        }

    /** Reads the GPS position from the original file's EXIF. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readExifCoordinates(uri: Uri): Pair<Double, Double>? = try {
        resolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { stream ->
            ExifInterface(stream).latLong?.let { it[0] to it[1] }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read the EXIF coordinates", e)
        null
    }

    /**
     * A cursor over the photos taken on the same month/day as [date] in earlier years, oldest first,
     * or null when there is no such year. The time zone is read per query so a changed device zone is
     * picked up; it only sets the day boundaries, the caller decides which date "today" is.
     */
    private fun queryOnThisDay(date: LocalDate, zone: ZoneId): Cursor? {
        // Without the permission MediaStore returns an empty cursor, which must not look like an empty day
        if (!hasMediaPermission()) throw PermissionDeniedException("Media read permission is not granted")
        val monthDay = MonthDay.from(date)
        val ranges = (date.year - 1 downTo date.year - PhotosConstants.MAX_YEARS_BACK)
            // A Feb 29 photo is only looked up in leap years, by design: other years are skipped,
            // not mapped to Feb 28 or Mar 1
            .filter { monthDay.isValidYear(it) }
            .map { year ->
                val day = monthDay.atYear(year)
                day.atStartOfDay(zone).toInstant().toEpochMilli() to
                    day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        if (ranges.isEmpty()) return null

        val selection = ranges.joinToString(" OR ") {
            "(${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?)"
        }
        val args = ranges.flatMap { listOf(it.first.toString(), it.second.toString()) }.toTypedArray()
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} ASC"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
            }
            resolver.query(COLLECTION_URI, PROJECTION, queryArgs, null)
        } else {
            resolver.query(COLLECTION_URI, PROJECTION, selection, args, sort)
        }
    }

    private fun Cursor.toPhoto(zone: ZoneId): Photo {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        return Photo(
            id = id,
            uri = ContentUris.withAppendedId(COLLECTION_URI, id),
            dateTaken = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)))
                .atZone(zone)
                .toLocalDate(),
            sizeBytes = getLong(getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
        )
    }


    private companion object {
        const val TAG = "PhotoRepositoryImpl"

        // DATE_MODIFIED and DISPLAY_NAME are for the photos without DATE_TAKEN: the cache key and the file-name date
        val INDEX_PROJECTION = listOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DISPLAY_NAME,
        )

        // What checking a cached date against the file needs
        val CACHE_PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
        )

        /** How many undated photos get their raw EXIF tags logged. */
        const val DIAGNOSTIC_SAMPLES = 10

        // SQLite allows 999 variables per statement on older Android versions
        const val SQL_VARIABLE_LIMIT = 500
        val COLLECTION_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // The LATITUDE/LONGITUDE columns are deprecated and empty since API 29, so they are
        // only read below it, for a single photo (see readExifCoordinates for API 29+).
        @Suppress("DEPRECATION")
        const val LATITUDE_COLUMN = MediaStore.Images.ImageColumns.LATITUDE

        @Suppress("DEPRECATION")
        const val LONGITUDE_COLUMN = MediaStore.Images.ImageColumns.LONGITUDE

        val PROJECTION: Array<String> = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
        )
    }
}
