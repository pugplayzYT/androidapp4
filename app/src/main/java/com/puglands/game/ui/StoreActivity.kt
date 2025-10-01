package com.puglands.game.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.puglands.game.R
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
import java.util.concurrent.TimeUnit

class StoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoreBinding
    // Firebase components removed
    private var user: User? = null

    // Ads variables
    private var voucherRewardedAd: RewardedAd? = null
    private var boostRewardedAd: RewardedAd? = null
    private var rangeRewardedAd: RewardedAd? = null
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917"

    private var incomeTimerJob: Job? = null
    private var rangeTimerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}

        loadVoucherRewardedAd()
        loadBoostRewardedAd()
        loadRangeRewardedAd()

        loadUserData()

        binding.watchAdButton.setOnClickListener { showVoucherRewardedAd() }
        binding.watchBoostAdButton.setOnClickListener { showBoostRewardedAd() }
        binding.watchRangeBoostAdButton.setOnClickListener { showRangeRewardedAd() }
    }

    override fun onResume() {
        super.onResume()
        // Re-load data when activity resumes to get latest state
        loadUserData()
    }

    override fun onStop() {
        super.onStop()
        incomeTimerJob?.cancel()
        rangeTimerJob?.cancel()
    }

    // Helper to convert ISO String timestamp to Date object
    private fun isoDateToDate(isoString: String?): Date? {
        if (isoString == null) return null
        return try {
            // Flask server uses UTC with Z or +00:00. This handles ISO 8601.
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ", Locale.US).parse(isoString.replace("Z", "+00:00"))
        } catch (e: ParseException) {
            null
        }
    }


    private fun loadUserData() {
        val userId = ApiClient.currentAuthUser?.uid ?: return
        lifecycleScope.launch {
            try {
                // API Call to Flask server to get user data
                user = ApiClient.getUser(userId)
                updateUI()
                startBoostTimers()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        user?.let {
            binding.vouchersTextView.text = "You have ${it.landVouchers} Land Vouchers"
        }
        binding.watchAdButton.isEnabled = voucherRewardedAd != null
        binding.watchBoostAdButton.isEnabled = boostRewardedAd != null
        binding.watchRangeBoostAdButton.isEnabled = rangeRewardedAd != null
    }

    private fun startBoostTimers() {
        // Income Boost Timer
        incomeTimerJob?.cancel()
        val boostEndTime = isoDateToDate(user?.boostEndTime)?.time ?: 0
        updateTimer(boostEndTime, binding.boostTimerTextView) { incomeTimerJob = it }

        // Range Boost Timer
        rangeTimerJob?.cancel()
        val rangeBoostEndTime = isoDateToDate(user?.rangeBoostEndTime)?.time ?: 0
        updateTimer(rangeBoostEndTime, binding.rangeBoostTimerTextView) { rangeTimerJob = it }
    }

    private fun updateTimer(endTime: Long, textView: View, jobSetter: (Job?) -> Unit) {
        if (endTime > System.currentTimeMillis()) {
            textView.visibility = View.VISIBLE
            jobSetter(lifecycleScope.launch {
                while (true) {
                    val remainingTime = endTime - System.currentTimeMillis()
                    if (remainingTime <= 0) {
                        textView.visibility = View.GONE
                        // Re-load user data to update the local 'user' object and sync the state
                        loadUserData()
                        break
                    }
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60
                    // Check the view ID to determine the text format
                    val prefix = if (textView.id == R.id.boostTimerTextView) "Boost" else "Range Boost"
                    (textView as android.widget.TextView).text = String.format("%s active: %02d:%02d", prefix, minutes, seconds)
                    delay(1000)
                }
            })
        } else {
            textView.visibility = View.GONE
            jobSetter(null)
        }
    }

    // --- Voucher Ad Logic ---
    private fun loadVoucherRewardedAd() {
        binding.watchAdButton.isEnabled = false
        val adRequest = AdManagerAdRequest.Builder().build()
        RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                voucherRewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                voucherRewardedAd = ad
                binding.watchAdButton.isEnabled = true
            }
        })
    }

    private fun showVoucherRewardedAd() {
        voucherRewardedAd?.let { ad ->
            ad.show(this) { _ -> grantVoucher() }
            voucherRewardedAd = null
            loadVoucherRewardedAd()
        } ?: run {
            Toast.makeText(this, "The ad wasn't ready. Please try again.", Toast.LENGTH_SHORT).show()
            loadVoucherRewardedAd()
        }
    }

    private fun grantVoucher() {
        lifecycleScope.launch {
            try {
                // API Call to Flask server to grant voucher
                val updatedUser = ApiClient.grantVoucher()
                user = updatedUser // Update local state
                updateUI()
                Toast.makeText(this@StoreActivity, "🎉 You earned 1 Land Voucher! 🎉", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Grant failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Boost Ad Logic ---
    private fun loadBoostRewardedAd() {
        binding.watchBoostAdButton.isEnabled = false
        val adRequest = AdManagerAdRequest.Builder().build()
        RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                boostRewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                boostRewardedAd = ad
                binding.watchBoostAdButton.isEnabled = true
            }
        })
    }

    private fun showBoostRewardedAd() {
        boostRewardedAd?.let { ad ->
            ad.show(this) { _ -> grantBoost() }
            boostRewardedAd = null
            loadBoostRewardedAd()
        } ?: run {
            Toast.makeText(this, "The boost ad wasn't ready. Please try again.", Toast.LENGTH_SHORT).show()
            loadBoostRewardedAd()
        }
    }

    private fun grantBoost() {
        lifecycleScope.launch {
            try {
                // API Call to Flask server to grant income boost
                val updatedUser = ApiClient.grantBoost()
                user = updatedUser // Update local state
                startBoostTimers() // Restart timer with new end time
                Toast.makeText(this@StoreActivity, "🚀 20x Boost Activated for 10 minutes! 🚀", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Grant failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Range Boost Ad Logic ---
    private fun loadRangeRewardedAd() {
        binding.watchRangeBoostAdButton.isEnabled = false
        val adRequest = AdManagerAdRequest.Builder().build()
        RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rangeRewardedAd = null
            }
            override fun onAdLoaded(ad: RewardedAd) {
                rangeRewardedAd = ad
                binding.watchRangeBoostAdButton.isEnabled = true
            }
        })
    }

    private fun showRangeRewardedAd() {
        rangeRewardedAd?.let { ad ->
            ad.show(this) { _ -> grantRangeBoost() }
            rangeRewardedAd = null
            loadRangeRewardedAd()
        } ?: run {
            Toast.makeText(this, "The range boost ad wasn't ready. Please try again.", Toast.LENGTH_SHORT).show()
            loadRangeRewardedAd()
        }
    }

    private fun grantRangeBoost() {
        lifecycleScope.launch {
            try {
                // API Call to Flask server to grant range boost
                val updatedUser = ApiClient.grantRangeBoost()
                user = updatedUser // Update local state
                startBoostTimers() // Restart range timer with new end time
                Toast.makeText(this@StoreActivity, "🛰️ 67% Range Boost Activated for 5 minutes! 🛰️", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Grant failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}