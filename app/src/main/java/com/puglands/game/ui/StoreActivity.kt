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
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917"

    private var timerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}

        loadVoucherRewardedAd()
        loadBoostRewardedAd()
        loadUserData()

        binding.watchAdButton.setOnClickListener { showVoucherRewardedAd() }
        binding.watchBoostAdButton.setOnClickListener { showBoostRewardedAd() }
    }

    override fun onStop() {
        super.onStop()
        timerJob?.cancel()
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
                startBoostTimer()
            }
    }

    private fun updateUI() {
        user?.let {
            binding.vouchersTextView.text = "You have ${it.landVouchers} Land Vouchers"
        }
        binding.watchAdButton.isEnabled = voucherRewardedAd != null
        binding.watchBoostAdButton.isEnabled = boostRewardedAd != null
    }

    private fun startBoostTimer() {
        timerJob?.cancel()
        val boostEndTime = user?.boostEndTime?.time ?: return

        if (boostEndTime > System.currentTimeMillis()) {
            binding.boostTimerTextView.visibility = View.VISIBLE
            timerJob = lifecycleScope.launch {
                while (true) {
                    val remainingTime = boostEndTime - System.currentTimeMillis()
                    if (remainingTime <= 0) {
                        binding.boostTimerTextView.visibility = View.GONE
                        break
                    }
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60
                    binding.boostTimerTextView.text = String.format("Boost active: %02d:%02d", minutes, seconds)
                    delay(1000)
                }
            }
        } else {
            binding.boostTimerTextView.visibility = View.GONE
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
            ad.show(this) { grantVoucher() }
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
            ad.show(this) { grantBoost() }
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
}