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

        binding.loginButton.setOnClickListener {
            loginUser()
        }
        binding.signUpButton.setOnClickListener {
            signUpUser()
        }
    }

    private fun saveSession(uid: String, name: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
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
                // API Call to Flask server for signup
                val authUser = ApiClient.signup(name, email, password)
                saveSession(authUser.uid, authUser.name)
                navigateToMain()
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
                // API Call to Flask server for login
                val loginResponse = ApiClient.login(email, password)

                // Save session right after successful login
                saveSession(loginResponse.uid, loginResponse.name)

                // Show offline earnings dialog if there are earnings
                if (loginResponse.offlineEarnings > 0.0000000001) {
                    val formattedEarnings = String.format(Locale.US, "%.11f", loginResponse.offlineEarnings).trimEnd('0').trimEnd('.')
                    AlertDialog.Builder(this@AuthActivity)
                        .setTitle("💸 Welcome Back! 💸")
                        .setMessage("You earned:\n\n$formattedEarnings Pugbucks\n\nwhile you were away.")
                        .setPositiveButton("Awesome!", null)
                        .setCancelable(false)
                        .show()
                }

                navigateToMain()
            } catch (e: Exception) {
                Toast.makeText(baseContext, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}