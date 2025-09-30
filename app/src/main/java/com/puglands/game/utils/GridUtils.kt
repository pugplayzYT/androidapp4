package com.puglands.game.utils

import org.maplibre.android.geometry.LatLng
import kotlin.math.*

/**
 * A utility object for handling conversions between Lat/Lon and the game's grid system.
 * It also provides distance calculation.
 */
object GridUtils {
    private const val GRID_WIDTH_METERS = 16.0
    private const val GRID_HEIGHT_METERS = 16.0

    // Use the standard WGS84 Earth radius for Mercator projections
    private const val EARTH_RADIUS_METERS = 6378137.0

    /**
     * Converts geographic coordinates (latitude, longitude) to grid coordinates (gx, gy)
     * using the accurate Web Mercator projection formula to match the map.
     */
    fun latLonToGrid(latLng: LatLng): Pair<Int, Int> {
        val latRad = Math.toRadians(latLng.latitude)
        val lonRad = Math.toRadians(latLng.longitude)

        // Project Lat/Lon to world meters using Web Mercator formulas
        val x = EARTH_RADIUS_METERS * lonRad
        val y = EARTH_RADIUS_METERS * ln(tan(PI / 4 + latRad / 2))

        // Convert world meters to grid coordinates
        val gx = floor(x / GRID_WIDTH_METERS).toInt()
        val gy = floor(y / GRID_HEIGHT_METERS).toInt()

        return Pair(gx, gy)
    }

    /**
     * Converts grid coordinates (gx, gy) back to the center LatLng of that grid cell
     * using the inverse Web Mercator projection.
     * This overload is used when grid coordinates are provided as Doubles (e.g., gx + 0.5 for center).
     */
    fun gridToLatLon(gx: Double, gy: Double): LatLng { // 👈 FIX: Accepts Double parameters
        // Get the world meter coordinates for the specific grid point (could be a center, corner, etc.)
        val centerX = gx * GRID_WIDTH_METERS
        val centerY = gy * GRID_HEIGHT_METERS

        // Inverse project world meters back to Lat/Lon
        val lonRad = centerX / EARTH_RADIUS_METERS
        val latRad = 2 * atan(exp(centerY / EARTH_RADIUS_METERS)) - (PI / 2)

        val lat = Math.toDegrees(latRad)
        val lon = Math.toDegrees(lonRad)

        return LatLng(lat, lon)
    }

    /**
     * Converts grid coordinates (gx, gy) back to the center LatLng of that grid cell
     * using the inverse Web Mercator projection.
     * This overload is used when grid coordinates are provided as Ints (e.g., corners).
     */
    fun gridToLatLon(gx: Int, gy: Int): LatLng {
        // This just calls the new Double version to avoid code duplication
        return gridToLatLon(gx.toDouble(), gy.toDouble())
    }

    /**
     * Calculates the distance between two LatLng points in meters using the Haversine formula.
     * This is a highly accurate way to calculate distance on a sphere.
     */
    fun distanceInMeters(point1: LatLng, point2: LatLng): Double {
        val lat1Rad = Math.toRadians(point1.latitude)
        val lon1Rad = Math.toRadians(point1.longitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val lon2Rad = Math.toRadians(point2.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        return EARTH_RADIUS_METERS * c
    }
}