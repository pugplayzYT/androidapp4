package com.puglands.game.data.database

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a single plot of land, now with owner info.
 */
data class Land(
    // Grid coordinates
    val gx: Int = 0,
    val gy: Int = 0,
    val pps: Double = 0.0,

    // NEW: Fields to identify the owner
    val ownerId: String = "",
    val ownerName: String = "",

    @ServerTimestamp val purchasedAt: Date? = null
)