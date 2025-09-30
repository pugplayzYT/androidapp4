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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.puglands.game.data.database.User
import com.puglands.game.databinding.ActivityStoreBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

// NOTE: To use Google's Test Ads without an AdMob account, you MUST add the
// test Application ID to your AndroidManifest.xml file inside the <application> tag:
//
// <meta-data
//      android:name="com.google.android.gms.ads.APPLICATION_ID"
//      android:value="ca-app-pub-3940256099942544~3347511713"/>
//
// You also need to add this dependency to your app's build.gradle file:
// implementation 'com.google.android.gms:play-services-ads:23.1.0'

class StoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoreBinding
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var user: User? = null

    // Ads variables
    private var voucherRewardedAd: RewardedAd? = null
    private var boostRewardedAd: RewardedAd? = null
    private var rangeRewardedAd: RewardedAd? = null // NEW: Range Boost Ad declaration
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917"

    private var incomeTimerJob: Job? = null // Renamed
    private var rangeTimerJob: Job? = null // NEW: Separate job for range boost timer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}

        loadVoucherRewardedAd()
        loadBoostRewardedAd()
        loadRangeRewardedAd() // NEW: Load the new ad
        loadUserData()

        binding.watchAdButton.setOnClickListener { showVoucherRewardedAd() }
        binding.watchBoostAdButton.setOnClickListener { showBoostRewardedAd() }
        binding.watchRangeBoostAdButton.setOnClickListener { showRangeRewardedAd() } // NEW: Set listener
    }

    override fun onStop() {
        super.onStop()
        incomeTimerJob?.cancel() // Changed from timerJob
        rangeTimerJob?.cancel() // NEW: Cancel range timer
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load user data.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                user = snapshot?.toObject<User>()
                updateUI()
                startBoostTimers() // Renamed call
            }
    }

    private fun updateUI() {
        user?.let {
            binding.vouchersTextView.text = "You have ${it.landVouchers} Land Vouchers"
        }
        binding.watchAdButton.isEnabled = voucherRewardedAd != null
        binding.watchBoostAdButton.isEnabled = boostRewardedAd != null
        binding.watchRangeBoostAdButton.isEnabled = rangeRewardedAd != null // NEW: Update button state
    }

    // Renamed and updated to handle both timers
    private fun startBoostTimers() {
        // Income Boost Timer
        incomeTimerJob?.cancel()
        val boostEndTime = user?.boostEndTime?.time ?: 0
        updateTimer(boostEndTime, binding.boostTimerTextView) { incomeTimerJob = it }

        // Range Boost Timer (NEW)
        rangeTimerJob?.cancel()
        val rangeBoostEndTime = user?.rangeBoostEndTime?.time ?: 0
        updateTimer(rangeBoostEndTime, binding.rangeBoostTimerTextView) { rangeTimerJob = it }
    }

    // NEW: Helper function to manage timers
    private fun updateTimer(endTime: Long, textView: View, jobSetter: (Job?) -> Unit) {
        if (endTime > System.currentTimeMillis()) {
            textView.visibility = View.VISIBLE
            jobSetter(lifecycleScope.launch {
                while (true) {
                    val remainingTime = endTime - System.currentTimeMillis()
                    if (remainingTime <= 0) {
                        textView.visibility = View.GONE
                        break
                    }
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60
                    // Check the view ID to determine the text format
                    val prefix = if (textView.id == com.puglands.game.R.id.boostTimerTextView) "Boost" else "Range Boost"
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
            // FIX: Explicitly ignore the rewardItem parameter to resolve inference error
            ad.show(this) { _ -> grantVoucher() }
            voucherRewardedAd = null
            loadVoucherRewardedAd()
        } ?: run {
            Toast.makeText(this, "The ad wasn't ready. Please try again.", Toast.LENGTH_SHORT).show()
            loadVoucherRewardedAd()
        }
    }

    private fun grantVoucher() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update("landVouchers", FieldValue.increment(1))
            .addOnSuccessListener {
                Toast.makeText(this, "🎉 You earned 1 Land Voucher! 🎉", Toast.LENGTH_LONG).show()
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
            // FIX: Explicitly ignore the rewardItem parameter to resolve inference error
            ad.show(this) { _ -> grantBoost() }
            boostRewardedAd = null
            loadBoostRewardedAd()
        } ?: run {
            Toast.makeText(this, "The boost ad wasn't ready. Please try again.", Toast.LENGTH_SHORT).show()
            loadBoostRewardedAd()
        }
    }

    private fun grantBoost() {
        val userId = auth.currentUser?.uid ?: return
        val boostDurationMillis = 10 * 60 * 1000L
        val newBoostEndTime = Date(System.currentTimeMillis() + boostDurationMillis)

        db.collection("users").document(userId)
            .update("boostEndTime", newBoostEndTime)
            .addOnSuccessListener {
                Toast.makeText(this, "🚀 20x Boost Activated for 10 minutes! 🚀", Toast.LENGTH_LONG).show()
            }
    }

    // --- NEW: Range Boost Ad Logic ---
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
            // FIX: Explicitly ignore the rewardItem parameter to resolve inference error
            ad.show(this) { _ -> grantRangeBoost() }
            rangeRewardedAd = null
            loadRangeRewardedAd()
        } ?: run {
            Toast.makeText(this, "The range boost ad wasn't ready. Please try again.", Toast.LENGTH_SHORT).show()
            loadRangeRewardedAd()
        }
    }

    private fun grantRangeBoost() {
        val userId = auth.currentUser?.uid ?: return
        val boostDurationMillis = 5 * 60 * 1000L // 5 minutes
        val newBoostEndTime = Date(System.currentTimeMillis() + boostDurationMillis)

        db.collection("users").document(userId)
            .update("rangeBoostEndTime", newBoostEndTime)
            .addOnSuccessListener {
                Toast.makeText(this, "🛰️ 67% Range Boost Activated for 5 minutes! 🛰️", Toast.LENGTH_LONG).show()
            }
    }
}