package com.kllxx.memorylane.core.photos.data.geocoder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/** What the geocoder said about a position. Only [Failure] is worth asking again later. */
internal sealed interface GeocodeResult {
    /** The place as "City, Country". */
    data class Found(val place: String) : GeocodeResult

    /** The geocoder answered, but knows no usable place for the position. */
    data object NoResult : GeocodeResult

    /** The geocoder is missing or could not answer (no network, a service error). */
    data object Failure : GeocodeResult
}

internal class LocationGeocoder internal constructor(
    private val geocoderProvider: () -> Geocoder,
    private val sdkInt: Int,
    private val ioDispatcher: CoroutineDispatcher,
    private val isPresent: () -> Boolean,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        // Built per lookup, so the language that is current at that moment is the one the place is named in
        geocoderProvider = { Geocoder(context, Locale.getDefault()) },
        sdkInt = Build.VERSION.SDK_INT,
        ioDispatcher = Dispatchers.IO,
        isPresent = { Geocoder.isPresent() },
    )

    /** "City, Country" in the current language; see [formatPlace] for the fallbacks. Never throws, apart from cancellation. */
    @SuppressLint("NewApi") // the API level is checked through the injectable sdkInt
    suspend fun getLocation(latitude: Double, longitude: Double): GeocodeResult = try {
        if (!isPresent()) {
            GeocodeResult.Failure
        } else {
            val geocoder = geocoderProvider()
            val address = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                fetchAsync(geocoder, latitude, longitude)
            } else {
                fetchBlocking(geocoder, latitude, longitude)
            }
            when (address) {
                is Lookup.Failed -> GeocodeResult.Failure
                is Lookup.Answer -> address.address?.let(::format)?.let { GeocodeResult.Found(it) }
                    ?: GeocodeResult.NoResult
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Could not geocode a location")
        GeocodeResult.Failure
    }

    private sealed interface Lookup {
        data class Answer(val address: Address?) : Lookup
        data object Failed : Lookup
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun fetchAsync(geocoder: Geocoder, latitude: Double, longitude: Double): Lookup =
        suspendCancellableCoroutine { continuation ->
            // The platform call has no cancel handle: on cancellation the late callback is just dropped
            var cancelled = false
            continuation.invokeOnCancellation { cancelled = true }
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (!cancelled) continuation.resume(Lookup.Answer(addresses.firstOrNull()))
                    }

                    override fun onError(errorMessage: String?) {
                        if (!cancelled) continuation.resume(Lookup.Failed)
                    }
                },
            )
        }

    @Suppress("DEPRECATION")
    private suspend fun fetchBlocking(geocoder: Geocoder, latitude: Double, longitude: Double): Lookup =
        withContext(ioDispatcher) {
            // An empty or null list means "no match"; a missing network or service throws instead
            Lookup.Answer(geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull())
        }

    private fun format(address: Address): String? =
        formatPlace(address.locality, address.subAdminArea, address.adminArea, address.countryName)

    internal companion object {
        /** How missing GPS data is commonly stored: a position of exactly 0/0. */
        const val INVALID_COORDINATE = 0.0
    }
}
