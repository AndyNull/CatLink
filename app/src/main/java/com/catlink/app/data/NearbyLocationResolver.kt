package com.catlink.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NearbyArea(val city: String, val district: String, val attribution: String = "")

object NearbyLocationResolver {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun currentArea(
        context: Context,
        serverGeocoder: suspend (Double, Double) -> Result<NearbyArea>,
    ): Result<NearbyArea> = runCatching {
        require(hasPermission(context)) { "需要定位权限" }
        val location = runCatching { withTimeout(12_000) { currentLocation(context) } }
            .getOrElse { throw IllegalStateException("暂时无法获取当前位置") }
        runCatching { withTimeout(8_000) { areaFromAddress(reverseGeocode(context, location)) } }
            .getOrElse {
                serverGeocoder(location.latitude, location.longitude)
                    .getOrElse { throw IllegalStateException("暂时无法识别当前位置，请稍后重试") }
            }
    }

    suspend fun ipArea(serverIPGeocoder: suspend () -> Result<NearbyArea>): Result<NearbyArea> =
        serverIPGeocoder().map { it.copy(attribution = it.attribution.ifBlank { "IP 粗略定位" }) }

    suspend fun currentCoordinates(context: Context): Result<Pair<Double, Double>> = runCatching {
        require(hasPermission(context)) { "需要定位权限" }
        val location = withTimeout(12_000) { currentLocation(context) }
        location.latitude to location.longitude
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter(manager::isProviderEnabled)
        val provider = providers.firstOrNull()
            ?: return@suspendCancellableCoroutine continuation.resumeWithException(IllegalStateException("请开启系统定位"))
        providers.mapNotNull(manager::getLastKnownLocation)
            .maxByOrNull(Location::getElapsedRealtimeNanos)
            ?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= 10 * 60 * 1_000_000_000L }
            ?.let {
                continuation.resume(it)
                return@suspendCancellableCoroutine
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                if (location != null) continuation.resume(location)
                else continuation.resumeWithException(IllegalStateException("暂时无法获取当前位置"))
            }
            continuation.invokeOnCancellation { signal.cancel() }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
            }
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(context: Context, location: Location): Address {
        val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        } else {
            withContext(Dispatchers.IO) { geocoder.getFromLocation(location.latitude, location.longitude, 1).orEmpty() }
        }
        return addresses.firstOrNull() ?: error("无法解析当前位置")
    }

    private fun areaFromAddress(address: Address): NearbyArea {
        val city = address.locality.orEmpty().ifBlank { address.subAdminArea.orEmpty() }.ifBlank { address.adminArea.orEmpty() }
        val district = address.subLocality.orEmpty().ifBlank { address.locality.orEmpty().takeIf { it != city }.orEmpty() }
        require(city.isNotBlank() && district.isNotBlank())
        return NearbyArea(RegionData.cityName(city), RegionData.districtName(district))
    }
}
