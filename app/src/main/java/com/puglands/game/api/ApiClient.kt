package com.puglands.game.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.puglands.game.data.database.AuthUser
import com.puglands.game.data.database.Land
import com.puglands.game.data.database.LoginResponse
import com.puglands.game.data.database.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simplified HttpClient for the Puglands API.
 * Uses HttpURLConnection and Gson for network calls on a separate coroutine dispatcher.
 */
object ApiClient {
    private const val BASE_URL = "https://package-exists-pubs-flowers.trycloudflare.com"

    // Stores the authenticated user's ID and name for subsequent API calls
    var currentAuthUser: AuthUser? = null

    private val gson = Gson()

    // --- Private Networking Utility ---

    private suspend fun executeRequest(
        urlPath: String,
        method: String,
        body: Any? = null,
        authenticated: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL/$urlPath")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            if (authenticated) {
                val uid = currentAuthUser?.uid ?: throw IllegalStateException("User not authenticated.")
                // Flask server expects this header for authorization
                connection.setRequestProperty("X-User-Id", uid)
            }

            if (body != null) {
                connection.doOutput = true
                val jsonBody = gson.toJson(body)
                OutputStreamWriter(connection.outputStream).use {
                    it.write(jsonBody)
                    it.flush()
                }
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorBody = BufferedReader(InputStreamReader(errorStream)).use { it.readText() }

                // Attempt to parse the error message from the Flask JSON response
                val errorMap = gson.fromJson<Map<String, String>>(errorBody, object : TypeToken<Map<String, String>>() {}.type)
                val errorMessage = errorMap["error"] ?: "Unknown server error"
                throw Exception(errorMessage)
            }

            return@withContext BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // --- Auth Endpoints ---

    suspend fun signup(name: String, email: String, password: String): AuthUser {
        val body = mapOf("name" to name, "email" to email, "password" to password)
        val json = executeRequest("signup", "POST", body)
        return gson.fromJson(json, AuthUser::class.java).also { currentAuthUser = it }
    }

    suspend fun login(email: String, password: String): LoginResponse {
        val body = mapOf("email" to email, "password" to password)
        val json = executeRequest("login", "POST", body)
        return gson.fromJson(json, LoginResponse::class.java).also { currentAuthUser = AuthUser(it.uid, it.name) }
    }

    // --- User Data Endpoints ---

    suspend fun getUser(uid: String): User {
        val json = executeRequest("user/$uid", "GET", authenticated = true)
        return gson.fromJson(json, User::class.java)
    }

    suspend fun updateUser(data: Map<String, Any>): User {
        val uid = currentAuthUser?.uid ?: throw IllegalStateException("User not authenticated.")
        val json = executeRequest("user/$uid", "PUT", data, authenticated = true)
        return gson.fromJson(json, User::class.java)
    }

    // --- Land Endpoints ---

    suspend fun getAllLands(): List<Land> {
        val json = executeRequest("lands", "GET")
        val type = object : TypeToken<List<Land>>() {}.type
        return gson.fromJson(json, type)
    }

    suspend fun getUserLands(uid: String): List<Land> {
        val json = executeRequest("lands/user/$uid", "GET", authenticated = true)
        val type = object : TypeToken<List<Land>>() {}.type
        return gson.fromJson(json, type)
    }

    suspend fun acquireLand(gx: Int, gy: Int, method: String): User {
        val body = mapOf("gx" to gx, "gy" to gy, "method" to method)
        val json = executeRequest("acquire_land", "POST", body, authenticated = true)
        val wrapperType = object : TypeToken<Map<String, Any>>() {}.type
        val wrapper = gson.fromJson<Map<String, Any>>(json, wrapperType)
        // Extract the updated User object from the server's response wrapper
        val userJson = gson.toJson(wrapper["user"])
        return gson.fromJson(userJson, User::class.java)
    }

    suspend fun bulkClaim(plots: List<Map<String, Int>>): User {
        val body = mapOf("plots" to plots)
        val json = executeRequest("bulk_claim_with_vouchers", "POST", body, authenticated = true)
        val wrapperType = object : TypeToken<Map<String, Any>>() {}.type
        val wrapper = gson.fromJson<Map<String, Any>>(json, wrapperType)
        // Extract the updated User object from the server's response wrapper
        val userJson = gson.toJson(wrapper["user"])
        return gson.fromJson(userJson, User::class.java)
    }

    suspend fun exchangePugCoins(amount: Double): User {
        val body = mapOf("amount" to amount)
        val json = executeRequest("exchange_coins", "POST", body, authenticated = true)
        return gson.fromJson(json, User::class.java)
    }

    // --- Rewards/Admin Endpoints ---

    suspend fun grantVoucher(): User {
        val json = executeRequest("grant_voucher", "POST", authenticated = true)
        return gson.fromJson(json, User::class.java)
    }

    suspend fun grantBoost(): User {
        val json = executeRequest("grant_boost", "POST", authenticated = true)
        return gson.fromJson(json, User::class.java)
    }

    suspend fun grantRangeBoost(): User {
        val json = executeRequest("grant_range_boost", "POST", authenticated = true)
        return gson.fromJson(json, User::class.java)
    }

    suspend fun submitRedemption(amount: Double): User {
        val body = mapOf("amount" to amount)
        val json = executeRequest("redemptions", "POST", body, authenticated = true)
        val wrapperType = object : TypeToken<Map<String, Any>>() {}.type
        val wrapper = gson.fromJson<Map<String, Any>>(json, wrapperType)
        // Extract the updated User object from the server's response wrapper
        val userJson = gson.toJson(wrapper["user"])
        return gson.fromJson(userJson, User::class.java)
    }

    suspend fun grantPugbucks(amount: Double): User {
        val body = mapOf("amount" to amount)
        val json = executeRequest("grant_pugbucks", "POST", body, authenticated = true)
        return gson.fromJson(json, User::class.java)
    }
}