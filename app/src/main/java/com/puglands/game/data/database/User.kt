package com.puglands.game.data.database

import com.google.gson.annotations.SerializedName

/**
 * Clean data class for server communication.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val balance: Double = 0.0,
    val landVouchers: Int = 0,
    // Timestamps are now ISO 8601 Strings from the Flask server
    val lastSeen: String? = null,
    val boostEndTime: String? = null, // Tracks when the 20x income boost expires
    val rangeBoostEndTime: String? = null // Tracks when the range boost expires
)

/**
 * A simplified class for the client to store the current authenticated user's details.
 */
data class AuthUser(
    val uid: String,
    val name: String
)

/**
 * Wrapper for the /login response, containing the core user data and offline earnings.
 */
data class LoginResponse(
    val uid: String,
    val name: String,
    @SerializedName("offline_earnings")
    val offlineEarnings: Double,
    @SerializedName("user_data")
    val userData: User
)