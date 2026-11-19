package com.perfectgps.camera.model

import java.util.Objects

class GeoLocationSnapshot(
    private val latitude: Double,
    private val longitude: Double,
    private val accuracyMeters: Double,
    private val altitudeMeters: Double,
    private val speedMetersPerSecond: Double,
    private val bearingDegrees: Double,
    private val timestampMillis: Long,
    provider: String?,
    private val satellites: Int
) {
    private val provider: String = provider ?: "unknown"

    fun latitude(): Double = latitude

    fun longitude(): Double = longitude

    fun accuracyMeters(): Double = accuracyMeters

    fun altitudeMeters(): Double = altitudeMeters

    fun speedMetersPerSecond(): Double = speedMetersPerSecond

    fun bearingDegrees(): Double = bearingDegrees

    fun timestampMillis(): Long = timestampMillis

    fun provider(): String = provider

    fun satellites(): Int = satellites

    fun isValid(): Boolean =
        java.lang.Double.isFinite(latitude) &&
                java.lang.Double.isFinite(longitude) &&
                latitude >= -90.0 &&
                latitude <= 90.0 &&
                longitude >= -180.0 &&
                longitude <= 180.0 &&
                java.lang.Double.isFinite(accuracyMeters) &&
                accuracyMeters >= 0.0

    fun hasAltitude(): Boolean = java.lang.Double.isFinite(altitudeMeters)

    fun withLatitude(value: Double): GeoLocationSnapshot = GeoLocationSnapshot(
        value,
        longitude,
        accuracyMeters,
        altitudeMeters,
        speedMetersPerSecond,
        bearingDegrees,
        timestampMillis,
        provider,
        satellites
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is GeoLocationSnapshot) {
            return false
        }
        return java.lang.Double.compare(latitude, other.latitude) == 0 &&
                java.lang.Double.compare(longitude, other.longitude) == 0 &&
                java.lang.Double.compare(accuracyMeters, other.accuracyMeters) == 0 &&
                java.lang.Double.compare(altitudeMeters, other.altitudeMeters) == 0 &&
                java.lang.Double.compare(speedMetersPerSecond, other.speedMetersPerSecond) == 0 &&
                java.lang.Double.compare(bearingDegrees, other.bearingDegrees) == 0 &&
                timestampMillis == other.timestampMillis &&
                satellites == other.satellites &&
                provider == other.provider
    }

    override fun hashCode(): Int = Objects.hash(
        latitude,
        longitude,
        accuracyMeters,
        altitudeMeters,
        speedMetersPerSecond,
        bearingDegrees,
        timestampMillis,
        provider,
        satellites
    )
}
