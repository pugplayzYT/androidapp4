package com.puglands.game.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.puglands.game.api.ApiClient
import com.puglands.game.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch
import java.util.Locale

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    companion object {
        const val PREFS_NAME = "PuglandsPrefs"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { loginUser() }
        binding.signUpButton.setOnClickListener { signUpUser() }
    }

    private fun saveSession(uid: String, name: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_USER_ID, uid)
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    private fun signUpUser() {
        val name = binding.usernameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val authUser = ApiClient.signup(name, email, password)
                saveSession(authUser.uid, authUser.name)
                navigateToMain(0.0) // No offline earnings for a new user
            } catch (e: Exception) {
                Toast.makeText(baseContext, "Sign Up Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loginUser() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val loginResponse = ApiClient.login(email, password)
                saveSession(loginResponse.uid, loginResponse.name)

                // FIX: Navigate to main and pass the offline earnings to be shown
                navigateToMain(loginResponse.offlineEarnings)

            } catch (e: Exception) {
                Toast.makeText(baseContext, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain(offlineEarnings: Double) {
        // Show offline earnings dialog if there are any
        if (offlineEarnings > 0.0000000001) {
            val formattedEarnings = String.format(Locale.US, "%.11f", offlineEarnings).trimEnd('0').trimEnd('.')
            AlertDialog.Builder(this)
                .setTitle("💸 Welcome Back! 💸")
                .setMessage("You earned:\n\n$formattedEarnings Pug Coins\n\nwhile you were away.")
                .setPositiveButton("Awesome!") { _, _ ->
                    // Start MainActivity after the user dismisses the dialog
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .setCancelable(false)
                .show()
        } else {
            // If no earnings, go straight to the game
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}