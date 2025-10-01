package com.puglands.game.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.puglands.game.api.ApiClient
import com.puglands.game.data.database.User
import com.puglands.game.databinding.ActivityStoreBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.max

class StoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoreBinding
    private var user: User? = null

    private var voucherRewardedAd: RewardedAd? = null
    private var boostRewardedAd: RewardedAd? = null
    private var rangeRewardedAd: RewardedAd? = null
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917" // Test Ad ID

    // Jobs to manage the countdown timers on the buttons
    private var voucherCooldownJob: Job? = null
    private var boostCooldownJob: Job? = null
    private var rangeCooldownJob: Job? = null

    companion object {
        const val AD_COOLDOWN_HOURS = 23

        // 🚀 FIX: Robust date parser to handle inconsistent ISO 8601 strings from Flask
        private val DATE_FORMATS = arrayOf(
            // Flask default with microseconds and colon-separated timezone
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            // Format enforced in Python server code
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'",
            // Simpler formats (in case microseconds are missing)
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )

        fun isoDateToDate(isoString: String?): Date? {
            if (isoString.isNullOrEmpty()) return null

            // Clean the string by replacing the optional 'Z' (Zulu/UTC) with a format the parser expects
            val cleanString = isoString.replace("Z", "+0000").replace("+00:00", "+0000")

            for (formatString in DATE_FORMATS) {
                try {
                    val format = SimpleDateFormat(formatString, Locale.US)
                    // It's critical to set the timezone, so the time math is correct
                    format.timeZone = TimeZone.getTimeZone("UTC")

                    return format.parse(cleanString)
                } catch (e: Exception) {
                    // Try the next format
                }
            }
            return null // Parsing failed with all known formats
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}

        loadVoucherRewardedAd()
        loadBoostRewardedAd()
        loadRangeRewardedAd()

        binding.watchAdButton.setOnClickListener { showVoucherRewardedAd() }
        binding.watchBoostAdButton.setOnClickListener { showBoostRewardedAd() }
        binding.watchRangeBoostAdButton.setOnClickListener { showRangeRewardedAd() }
    }

    override fun onResume() {
        super.onResume()
        // Ensure data is refreshed on resume to check cooldown status
        loadUserData()
    }

    override fun onStop() {
        super.onStop()
        // Cancel all countdown timers when the activity is not visible
        voucherCooldownJob?.cancel()
        boostCooldownJob?.cancel()
        rangeCooldownJob?.cancel()
    }

    // ❌ REMOVE the buggy original isoDateToDate here. It's now in the companion object.
    // If you need a version of this in MainActivity, you must use the companion object's version.

    private fun loadUserData() {
        val userId = ApiClient.currentAuthUser?.uid ?: return
        lifecycleScope.launch {
            try {
                user = ApiClient.getUser(userId)
                updateUI()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        user?.let {
            binding.vouchersTextView.text = "You have ${it.landVouchers} Land Vouchers"

            // Check cooldowns for each ad type and update button states
            updateAdButtonState(binding.watchAdButton, it.lastVoucherAdWatch, "Watch Ad for a Land Voucher") { job -> voucherCooldownJob = job }
            updateAdButtonState(binding.watchBoostAdButton, it.lastBoostAdWatch, "Watch Ad for 20x Boost (10 mins)") { job -> boostCooldownJob = job }
            updateAdButtonState(binding.watchRangeBoostAdButton, it.lastRangeBoostAdWatch, "Watch Ad for 67% Range Boost (5 mins)") { job -> rangeCooldownJob = job }
        }
    }

    private fun updateAdButtonState(button: Button, lastWatchIso: String?, defaultText: String, jobSetter: (Job?) -> Unit) {
        jobSetter(null) // Cancel any existing job for this button

        // 🎯 FIX: Use the robust parser from the companion object
        val lastWatchDate = isoDateToDate(lastWatchIso)

        if (lastWatchDate == null) {
            button.isEnabled = true
            button.text = defaultText
            return
        }

        // Date math is correct since the parser enforces UTC
        val cooldownEndTime = lastWatchDate.time + TimeUnit.HOURS.toMillis(AD_COOLDOWN_HOURS.toLong())

        if (System.currentTimeMillis() < cooldownEndTime) {
            button.isEnabled = false
            // Start a countdown timer on the button
            jobSetter(lifecycleScope.launch {
                while(true) {
                    val remainingTime = cooldownEndTime - System.currentTimeMillis()
                    if (remainingTime <= 0) {
                        button.isEnabled = true
                        button.text = defaultText
                        break
                    }
                    val hours = TimeUnit.MILLISECONDS.toHours(remainingTime)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60
                    button.text = String.format(Locale.US, "Ready in %02d:%02d:%02d", hours, minutes, seconds)
                    delay(1000)
                }
            })
        } else {
            button.isEnabled = true
            button.text = defaultText
        }
    }

    // --- Ad Logic (Voucher, Boost, Range) ---
    private fun loadVoucherRewardedAd() {
        RewardedAd.load(this, adUnitId, AdManagerAdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { voucherRewardedAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { voucherRewardedAd = null }
        })
    }

    private fun showVoucherRewardedAd() {
        voucherRewardedAd?.show(this) { grantVoucher() } ?: run {
            Toast.makeText(this, "Ad not ready. Try again.", Toast.LENGTH_SHORT).show()
            loadVoucherRewardedAd()
        }
    }

    private fun grantVoucher() {
        lifecycleScope.launch {
            try {
                // This will send the request and update the user object on the server
                val updatedUser = ApiClient.grantVoucher()
                user = updatedUser
                updateUI() // Update local UI after server success
                Toast.makeText(this@StoreActivity, "🎉 You earned 1 Land Voucher! 🎉", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Reward failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadBoostRewardedAd() {
        RewardedAd.load(this, adUnitId, AdManagerAdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { boostRewardedAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { boostRewardedAd = null }
        })
    }

    private fun showBoostRewardedAd() {
        boostRewardedAd?.show(this) { grantBoost() } ?: run {
            Toast.makeText(this, "Ad not ready. Try again.", Toast.LENGTH_SHORT).show()
            loadBoostRewardedAd()
        }
    }

    private fun grantBoost() {
        lifecycleScope.launch {
            try {
                // This will send the request and update the user object on the server
                val updatedUser = ApiClient.grantBoost()
                user = updatedUser
                updateUI() // Update local UI after server success
                Toast.makeText(this@StoreActivity, "🚀 20x Boost Activated! 🚀", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Reward failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadRangeRewardedAd() {
        RewardedAd.load(this, adUnitId, AdManagerAdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rangeRewardedAd = ad }
            override fun onAdFailedToLoad(adError: LoadAdError) { rangeRewardedAd = null }
        })
    }

    private fun showRangeRewardedAd() {
        rangeRewardedAd?.show(this) { grantRangeBoost() } ?: run {
            Toast.makeText(this, "Ad not ready. Try again.", Toast.LENGTH_SHORT).show()
            loadRangeRewardedAd()
        }
    }

    private fun grantRangeBoost() {
        lifecycleScope.launch {
            try {
                // This will send the request and update the user object on the server
                val updatedUser = ApiClient.grantRangeBoost()
                user = updatedUser
                updateUI() // Update local UI after server success
                Toast.makeText(this@StoreActivity, "🛰️ Range Boost Activated! 🛰️", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Reward failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}