package com.perfectgps.camera.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PlaceNameResolver(
        context: Context,
        locale: Locale?
) {
    companion object {
        private const val MIN_MOVE_METERS = 150.0f

        /**
         * One round trip, several candidates. The first result is usually the coarsest
         * area match, so asking for more and scoring them is what surfaces a venue name.
         */
        private const val LOOKUP_RESULTS = 5

        @JvmStatic
        fun distanceMeters(
                latitudeA: Double,
                longitudeA: Double,
                latitudeB: Double,
                longitudeB: Double
        ): Double {
            val earthRadius = 6_371_000.0
            val lat1 = Math.toRadians(latitudeA)
            val lat2 = Math.toRadians(latitudeB)
            val deltaLat = lat2 - lat1
            val deltaLon = Math.toRadians(longitudeB - longitudeA)
            val a = Math.sin(deltaLat / 2.0) * Math.sin(deltaLat / 2.0) +
                    Math.cos(lat1) * Math.cos(lat2) *
                    Math.sin(deltaLon / 2.0) * Math.sin(deltaLon / 2.0)
            return earthRadius * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)))
        }

        private fun trimmed(value: String?): String = value?.trim() ?: ""
    }

    private val context: Context = context.applicationContext
    private val locale: Locale = locale ?: Locale.getDefault()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    @Volatile
    private var placeName: String = ""

    @Volatile
    private var placeDetail: String = ""

    @Volatile
    private var lastLookup: String = ""

    @Volatile
    private var lastLatitude: Double = Double.NaN

    @Volatile
    private var lastLongitude: Double = Double.NaN

    constructor(context: Context) : this(context, Locale.getDefault())

    fun placeName(): String = placeName

    /** Finer-grained than [placeName]: venue, road, sub-district, PIN. May be empty. */
    fun placeDetail(): String = placeDetail

    fun request(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return
        }
        if (lastLookup.isNotEmpty() && !movedFarEnough(latitude, longitude)) {
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            return
        }
        lastLatitude = latitude
        lastLongitude = longitude
        executor.execute { lookup(latitude, longitude) }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun movedFarEnough(latitude: Double, longitude: Double): Boolean {
        if (lastLatitude.isNaN() || lastLongitude.isNaN()) {
            return true
        }
        val meters = distanceMeters(lastLatitude, lastLongitude, latitude, longitude)
        return meters >= MIN_MOVE_METERS
    }

    @Suppress("DEPRECATION")
    private fun lookup(latitude: Double, longitude: Double) {
        var headline = ""
        var detail = ""
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, locale)
                val addresses = geocoder.getFromLocation(latitude, longitude, LOOKUP_RESULTS)
                if (addresses != null && addresses.isNotEmpty()) {
                    val best = pickBest(addresses)
                    headline = describe(best)
                    detail = describeDetail(best, headline)
                }
            }
        } catch (ignored: IOException) {
            headline = ""
            detail = ""
        } catch (ignored: RuntimeException) {
            headline = ""
            detail = ""
        }
        publish(headline, detail)
    }

    private fun pickBest(addresses: List<Address>): Address {
        var best = addresses[0]
        var bestScore = specificity(best)
        for (index in 1 until addresses.size) {
            val candidate = addresses[index]
            val score = specificity(candidate)
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    /**
     * How much a candidate actually names a place. Weighted towards street and premises
     * detail, which is what the coarse first result tends to lack.
     */
    private fun specificity(address: Address): Int {
        var score = 0
        if (trimmed(address.featureName).isNotEmpty()) {
            score += 2
        }
        if (address.maxAddressLineIndex > 0) {
            score++
        }
        if (trimmed(address.thoroughfare).isNotEmpty()) {
            score += 2
        }
        if (hasPremises(address)) {
            score += 2
        }
        if (trimmed(address.subLocality).isNotEmpty()) {
            score++
        }
        if (trimmed(address.postalCode).isNotEmpty()) {
            score++
        }
        if (trimmed(address.locality).isNotEmpty()) {
            score++
        }
        return score
    }

    private fun hasPremises(address: Address): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    trimmed(address.premises).isNotEmpty()

    private fun describeDetail(address: Address, headline: String): String {
        val parts = ArrayList<String>()

        val venue = trimmed(address.featureName)
        addIfNew(parts, headline, venue)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addIfNew(parts, headline, trimmed(address.premises))
        }

        val road = trimmed(address.thoroughfare)
        val cross = trimmed(address.subThoroughfare)
        if (road.isNotEmpty()) {
            addIfNew(parts, headline, if (cross.isEmpty()) road else "$cross $road")
        }

        addIfNew(parts, headline, trimmed(address.subLocality))
        addIfNew(parts, headline, trimmed(address.postalCode))

        return parts.joinToString(", ")
    }

    private fun addIfNew(parts: ArrayList<String>, headline: String, value: String) {
        if (value.isEmpty()) {
            return
        }
        if (headline.isNotEmpty() && (headline.contains(value, ignoreCase = true) ||
                        value.contains(headline, ignoreCase = true))) {
            return
        }
        for (existing in parts) {
            if (existing.contains(value, ignoreCase = true) ||
                    value.contains(existing, ignoreCase = true)) {
                return
            }
        }
        parts.add(value)
    }

    private fun publish(resolved: String, detail: String) {
        inFlight.set(false)
        if (resolved.isEmpty()) {
            return
        }
        placeName = resolved
        placeDetail = detail
        lastLookup = resolved
    }

    private fun describe(address: Address): String {
        val locality = trimmed(address.locality)
        val subAdmin = trimmed(address.subAdminArea)
        val admin = trimmed(address.adminArea)
        val country = trimmed(address.countryName)
        val feature = trimmed(address.featureName)

        if (locality.isNotEmpty()) {
            if (subAdmin.isNotEmpty() && !subAdmin.equals(locality, ignoreCase = true)) {
                return "$locality, $subAdmin"
            }
            if (admin.isNotEmpty() && !admin.equals(locality, ignoreCase = true)) {
                return "$locality, $admin"
            }
            return locality
        }
        if (subAdmin.isNotEmpty()) {
            return subAdmin
        }
        if (admin.isNotEmpty()) {
            return admin
        }
        if (feature.isNotEmpty()) {
            return feature
        }
        return country
    }
}
