package com.perfectgps.camera.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.perfectgps.camera.model.GeoLocationSnapshot
import java.util.LinkedHashSet

class LocationController(context: Context, private val callback: Callback) : LocationListener {
    interface Callback {
        fun onLocationChanged(location: GeoLocationSnapshot?)

        fun onLocationUnavailable()
    }

    private val context: Context = context.applicationContext
    private val manager: LocationManager? =
            this.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
    private val activeProviders: MutableSet<String> = LinkedHashSet()
    private var lastLocation: GeoLocationSnapshot? = null
    private var started = false

    fun start() {
        if (started || manager == null || !hasLocationPermission()) {
            return
        }
        started = true
        activeProviders.clear()
        try {
            if (hasFineLocationPermission() && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                requestUpdates(LocationManager.GPS_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                requestUpdates(LocationManager.NETWORK_PROVIDER)
            }
            restoreBestLastLocation()
            if (activeProviders.isEmpty()) {
                callback.onLocationUnavailable()
            }
        } catch (exception: SecurityException) {
            stop()
            callback.onLocationUnavailable()
        }
    }

    fun stop() {
        if (!started || manager == null) {
            return
        }
        try {
            manager.removeUpdates(this)
        } catch (ignored: SecurityException) {
        }
        activeProviders.clear()
        started = false
    }

    fun lastLocation(): GeoLocationSnapshot? = lastLocation

    override fun onLocationChanged(location: Location) {
        val accuracy = if (location.hasAccuracy()) Math.max(0.0f, location.accuracy) else 0.0f
        val altitude = if (location.hasAltitude()) location.altitude else Double.NaN
        val speed = if (location.hasSpeed()) Math.max(0.0f, location.speed).toDouble() else Double.NaN
        val bearing = if (location.hasBearing()) location.bearing.toDouble() else Double.NaN
        var satellites = 0
        val extras = location.extras
        if (extras != null && extras.containsKey("satellites")) {
            satellites = extras.getInt("satellites", 0)
        }
        val snapshot = GeoLocationSnapshot(
                location.latitude,
                location.longitude,
                accuracy.toDouble(),
                altitude,
                speed,
                bearing,
                Math.max(location.time, 0L),
                safeProvider(location.provider),
                satellites
        )
        if (snapshot.isValid()) {
            lastLocation = snapshot
            callback.onLocationChanged(snapshot)
        }
    }

    override fun onProviderDisabled(provider: String) {
        callback.onLocationUnavailable()
    }

    override fun onProviderEnabled(provider: String) {
        try {
            requestUpdates(provider)
        } catch (ignored: SecurityException) {
            callback.onLocationUnavailable()
        }
    }

    @Deprecated("Deprecated in the location listener API")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
    }

    private fun requestUpdates(provider: String) {
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if ((!fineGranted && !coarseGranted) || activeProviders.contains(provider)) {
            return
        }
        manager!!.requestLocationUpdates(provider, 1_000L, 0.0f, this, Looper.getMainLooper())
        activeProviders.add(provider)
    }

    private fun restoreBestLastLocation() {
        if (!hasLocationPermission()) {
            return
        }
        var best: Location? = null
        for (provider in activeProviders) {
            try {
                val candidate = manager!!.getLastKnownLocation(provider)
                if (candidate != null && (best == null || candidate.time > best.time)) {
                    best = candidate
                }
            } catch (ignored: SecurityException) {
            }
        }
        best?.let { onLocationChanged(it) }
    }

    private fun hasLocationPermission(): Boolean = hasFineLocationPermission() ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

    private fun safeProvider(provider: String?): String =
            if (provider == null || provider.trim().isEmpty()) "unknown" else provider
}
