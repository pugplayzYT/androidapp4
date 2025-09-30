package com.puglands.game.data.database

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Clean data class for Firestore.
 * NOTE: All Room annotations like @Entity and @PrimaryKey have been removed.
 * @ServerTimestamp tells Firestore to automatically manage the timestamp on the server.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val balance: Double = 0.0,
    val landVouchers: Int = 0,
    @ServerTimestamp val lastSeen: Date? = null,
    val boostEndTime: Date? = null // Tracks when the 20x boost expires
)