package com.puglands.game.data.database

/**
 * Represents a single plot of land, now with owner info.
 */
data class Land(
    // Grid coordinates
    val gx: Int = 0,
    val gy: Int = 0,
    val pps: Double = 0.0,

    // Fields to identify the owner
    val ownerId: String = "",
    val ownerName: String = "",

    val purchasedAt: String? = null // ISO 8601 String
)