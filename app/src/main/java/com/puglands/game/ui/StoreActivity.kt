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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

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
        loadUserData()
    }

    override fun onStop() {
        super.onStop()
        // Cancel all countdown timers when the activity is not visible
        voucherCooldownJob?.cancel()
        boostCooldownJob?.cancel()
        rangeCooldownJob?.cancel()
    }

    private fun isoDateToDate(isoString: String?): Date? {
        if (isoString == null) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(isoString.replace("Z", "+0000"))
        } catch (e: ParseException) {
            null
        }
    }

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
        val lastWatchDate = isoDateToDate(lastWatchIso)

        if (lastWatchDate == null) {
            button.isEnabled = true
            button.text = defaultText
            return
        }

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
                val updatedUser = ApiClient.grantVoucher()
                user = updatedUser
                updateUI()
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
                val updatedUser = ApiClient.grantBoost()
                user = updatedUser
                updateUI()
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
                val updatedUser = ApiClient.grantRangeBoost()
                user = updatedUser
                updateUI()
                Toast.makeText(this@StoreActivity, "🛰️ Range Boost Activated! 🛰️", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Reward failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}