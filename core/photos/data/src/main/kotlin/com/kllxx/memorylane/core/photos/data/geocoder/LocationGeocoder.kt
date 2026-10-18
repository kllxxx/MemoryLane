package com.kllxx.memorylane.core.photos.data.geocoder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class LocationGeocoder internal constructor(
    private val geocoderProvider: () -> Geocoder,
    private val sdkInt: Int,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        geocoderProvider = { Geocoder(context, Locale.getDefault()) },
        sdkInt = Build.VERSION.SDK_INT,
        ioDispatcher = Dispatchers.IO,
    )

    /** Returns "City, Street", just "City" when there is no street, or null when unknown or on any error. */
    @SuppressLint("NewApi") // the API level is checked through the injectable sdkInt
    suspend fun getLocation(latitude: Double, longitude: Double): String? = try {
        val geocoder = geocoderProvider()
        val address = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            fetchAsync(geocoder, latitude, longitude)
        } else {
            fetchBlocking(geocoder, latitude, longitude)
        }
        address?.let(::format)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Could not geocode a location", e)
        null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun fetchAsync(geocoder: Geocoder, latitude: Double, longitude: Double): Address? =
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
                        if (!cancelled) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (!cancelled) continuation.resume(null)
                    }
                },
            )
        }

    @Suppress("DEPRECATION")
    private suspend fun fetchBlocking(geocoder: Geocoder, latitude: Double, longitude: Double): Address? =
        withContext(ioDispatcher) {
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }

    private fun format(address: Address): String? {
        val locality = address.locality ?: return null
        val thoroughfare = address.thoroughfare ?: return locality
        return "$locality, $thoroughfare"
    }

    internal companion object {
        /** How missing GPS data is commonly stored: a position of exactly 0/0. */
        const val INVALID_COORDINATE = 0.0
        private const val TAG = "LocationGeocoder"
    }
}
