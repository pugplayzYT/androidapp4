package com.puglands.game.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.puglands.game.R
import com.puglands.game.api.ApiClient
import com.puglands.game.data.database.AuthUser
import com.puglands.game.data.database.Land
import com.puglands.game.data.database.User
import com.puglands.game.databinding.ActivityMainBinding
import com.puglands.game.utils.GridUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var maplibreMap: MapLibreMap

    private var mediaPlayer: MediaPlayer? = null
    private var secretTapCounter = 0
    private var lastTapTime: Long = 0

    private val _userState = MutableStateFlow<User?>(null)
    val userState: StateFlow<User?> = _userState.asStateFlow()
    private val _allLands = MutableStateFlow<List<Land>>(emptyList())
    private val allLands: StateFlow<List<Land>> = _allLands.asStateFlow()

    private var playerLocation: LatLng? = null
    private var incomeBoostTimerJob: Job? = null
    private var rangeBoostTimerJob: Job? = null
    private var gameStatePollingJob: Job? = null

    private val locationRequest = LocationEngineRequest.Builder(1000L)
        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY).build()

    private val locationCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult?) {
            result?.lastLocation?.let { location ->
                val newLocation = LatLng(location.latitude, location.longitude)
                if (playerLocation == null) {
                    maplibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 16.0))
                }
                playerLocation = newLocation
                updatePlayerRangeLayer()
            }
        }
        override fun onFailure(exception: Exception) {
            Toast.makeText(this@MainActivity, "Could not get location.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val STARTING_BALANCE = 50.0
        private const val BASE_PLAYER_RANGE_METERS = 400.0
        private const val RANGE_MULTIPLIER = 1.67
        private const val LAND_COST = 50.0
        private const val LAND_PPS = 0.0000000011
        private const val ALL_LANDS_SOURCE_ID = "all-lands-source"
        private const val ALL_LANDS_FILL_LAYER_ID = "all-lands-fill-layer"
        private const val ALL_LANDS_TEXT_LAYER_ID = "all-lands-text-layer"
        private const val PLAYER_RANGE_SOURCE_ID = "player-range-source"
        private const val PLAYER_RANGE_LAYER_ID = "player-range-layer"
        private const val GRID_SOURCE_ID = "grid-source"
        private const val GRID_LAYER_ID = "grid-layer"
        private const val OWNER_NAME_PROPERTY_KEY = "owner_name"
        private const val IS_CURRENT_PLAYER_PROPERTY_KEY = "is_current_player"
        private const val PREFS_NAME = "PuglandsPrefs"

        // Helper to convert ISO String to Date
        fun isoDateToDate(isoString: String?): Date? {
            if (isoString == null) return null
            return try {
                // Handle ISO 8601 format including milliseconds and timezone
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ", Locale.US).parse(isoString.replace("Z", "+00:00"))
            } catch (e: ParseException) {
                null
            }
        }
    }

    private fun checkAndRestoreSession(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(AuthActivity.KEY_USER_ID, null)
        val name = prefs.getString(AuthActivity.KEY_USER_NAME, null)

        if (uid != null && name != null) {
            // Restore session state in the ApiClient
            ApiClient.currentAuthUser = AuthUser(uid, name)
            return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkAndRestoreSession()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        MapLibre.getInstance(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mapView.onCreate(savedInstanceState)
        setupBackgroundMusic()
        setupClickListeners()
        binding.mapView.getMapAsync { map ->
            maplibreMap = map
            setupMap()
        }
        lifecycleScope.launch {
            loadInitialGameDataAndStartPolling()
            observeGameStateUI()
            // The line below was the cause of the error and has been removed:
            // launchOnlineIncomeLoop()
        }
    }

    private fun setupClickListeners() {
        binding.plotsButton.setOnClickListener {
            startActivity(Intent(this, PlotsActivity::class.java))
        }
        binding.storeButton.setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }
        binding.uiCard.setOnClickListener { handleSecretTap() }
        binding.redeemButton.setOnClickListener { showRedeemDialog() }
        binding.bulkClaimButton.setOnClickListener { showBulkClaimDialog() }
        binding.exchangeButton.setOnClickListener { showExchangeDialog() }
    }

    private fun handleSecretTap() {
        if (ApiClient.currentAuthUser?.name != "pugplayzYT") return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime > 1000) {
            secretTapCounter = 0
        }
        lastTapTime = currentTime
        secretTapCounter++

        if (secretTapCounter == 7) {
            secretTapCounter = 0
            showAdminDialog()
            Toast.makeText(this, "🤫 Admin Panel Unlocked!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRedeemDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount to redeem (1-3)"
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Redeem Pug Coins")
            .setMessage("1 Pug Coin = \$0.50. You can redeem between 1 and 3 coins at a time.")
            .setView(container)
            .setPositiveButton("Request") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null || amount < 1.0 || amount > 3.0) {
                    Toast.makeText(this, "Invalid amount. Must be between 1 and 3.", Toast.LENGTH_LONG).show()
                } else {
                    processRedemptionRequest(amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processRedemptionRequest(amount: Double) {
        lifecycleScope.launch {
            try {
                val updatedUser = ApiClient.submitRedemption(amount)
                _userState.value = updatedUser
                Toast.makeText(this@MainActivity, "Redemption request sent! Pug will review it.", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error creating request: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAdminDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Enter amount of Pugbucks"
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Panel - Grant Pugbucks")
            .setView(input)
            .setPositiveButton("Grant") { _, _ ->
                input.text.toString().toDoubleOrNull()?.let { grantPugbucks(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun grantPugbucks(amount: Double) {
        lifecycleScope.launch {
            try {
                val updatedUser = ApiClient.grantPugbucks(amount)
                _userState.value = updatedUser
                Toast.makeText(this@MainActivity, "Granted $amount Pugbucks!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupBackgroundMusic() {
        mediaPlayer = MediaPlayer.create(this, R.raw.background_music)
        mediaPlayer?.isLooping = true
        mediaPlayer?.setVolume(0.4f, 0.4f)
    }

    private suspend fun refreshAllData() {
        val uid = ApiClient.currentAuthUser?.uid ?: return
        try {
            val user = ApiClient.getUser(uid)
            _userState.value = user
            val lands = ApiClient.getAllLands()
            _allLands.value = lands
            if(::maplibreMap.isInitialized && maplibreMap.style?.isFullyLoaded == true) {
                updateAllLandsLayer()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error refreshing data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadInitialGameDataAndStartPolling() {
        val uid = ApiClient.currentAuthUser?.uid ?: return
        gameStatePollingJob?.cancel()
        gameStatePollingJob = lifecycleScope.launch {
            while (true) {
                try {
                    // BUG FIX: The client now ONLY fetches data. The server handles all calculations.
                    val user = ApiClient.getUser(uid)
                    _userState.value = user

                    val lands = ApiClient.getAllLands()
                    _allLands.value = lands

                    if (::maplibreMap.isInitialized && maplibreMap.style?.isFullyLoaded == true) {
                        updateAllLandsLayer()
                    }
                } catch (e: Exception) {
                    // Handle polling error silently in the background
                }
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    private fun forceRefreshUserData() {
        val uid = ApiClient.currentAuthUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val user = ApiClient.getUser(uid)
                _userState.value = user
            } catch (e: Exception) {
                // Silent fail is fine for resume refresh
            }
        }
    }



    private fun getCurrentRangeMeters(): Double {
        val rangeBoostEndTime = isoDateToDate(_userState.value?.rangeBoostEndTime)
        val isBoostActive = rangeBoostEndTime != null && rangeBoostEndTime.after(Date())
        return if (isBoostActive) {
            BASE_PLAYER_RANGE_METERS * RANGE_MULTIPLIER
        } else {
            BASE_PLAYER_RANGE_METERS
        }
    }

    private fun updateBoostTimerUI(user: User) {
        val incomeBoostEndTime = isoDateToDate(user.boostEndTime)
        val rangeBoostEndTime = isoDateToDate(user.rangeBoostEndTime)

        incomeBoostTimerJob?.cancel()
        if (incomeBoostEndTime != null && incomeBoostEndTime.after(Date())) {
            binding.boostTimerTextView.isVisible = true
            incomeBoostTimerJob = lifecycleScope.launch {
                while(true) {
                    val remainingTime = incomeBoostEndTime.time - System.currentTimeMillis()
                    if (remainingTime <= 0) {
                        binding.boostTimerTextView.isVisible = false
                        break
                    }
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60
                    binding.boostTimerTextView.text = String.format("🚀 Boost: %02d:%02d", minutes, seconds)
                    delay(1000)
                }
            }
        } else {
            binding.boostTimerTextView.isVisible = false
        }

        binding.rangeBoostTimerTextView.let { rangeTimer ->
            rangeBoostTimerJob?.cancel()
            if (rangeBoostEndTime != null && rangeBoostEndTime.after(Date())) {
                rangeTimer.isVisible = true
                rangeBoostTimerJob = lifecycleScope.launch {
                    while(true) {
                        val remainingTime = rangeBoostEndTime.time - System.currentTimeMillis()
                        if (remainingTime <= 0) {
                            rangeTimer.isVisible = false
                            updatePlayerRangeLayer()
                            break
                        }
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60
                        rangeTimer.text = String.format("🛰️ Range: %02d:%02d", minutes, seconds)
                        delay(1000)
                    }
                }
            } else {
                rangeTimer.isVisible = false
            }
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "a moment"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun observeGameStateUI() {
        lifecycleScope.launch {
            userState.collect { user ->
                user?.let {
                    val pugbucksText = "Pugbucks: ${formatCurrency(it.balance)}"
                    val pugcoinsText = "Pug Coins: ${formatCurrency(it.pugCoins)}"
                    binding.balanceTextView.text = "$pugbucksText\n$pugcoinsText"
                    val myLandsCount = _allLands.value.count { l -> l.ownerId == user.uid }
                    binding.landsOwnedTextView.text = "Lands: $myLandsCount | Vouchers: ${it.landVouchers}"
                    updateBoostTimerUI(it)
                    updatePlayerRangeLayer()
                }
            }
        }

        lifecycleScope.launch {
            allLands.collect { lands ->
                val user = _userState.value
                user?.let {
                    val myLandsCount = lands.count { l -> l.ownerId == user.uid }
                    binding.landsOwnedTextView.text = "Lands: $myLandsCount | Vouchers: ${user.landVouchers}"
                }
                if(::maplibreMap.isInitialized && maplibreMap.style?.isFullyLoaded == true) {
                    updateAllLandsLayer()
                }
            }
        }
    }

    private fun showAcquireLandDialog(gx: Int, gy: Int) {
        val hasVoucher = (_userState.value?.landVouchers ?: 0) > 0
        val builder = AlertDialog.Builder(this)
            .setTitle("Acquire Land")
            .setMessage("How do you want to get this plot?")
            .setNegativeButton("Cancel", null)

        builder.setPositiveButton("Buy (${formatCurrency(LAND_COST)} Pugbucks)") { _, _ ->
            acquireLand(gx, gy, "BUY")
        }

        if (hasVoucher) {
            builder.setNeutralButton("Use Voucher (1)") { _, _ ->
                acquireLand(gx, gy, "VOUCHER")
            }
        }
        builder.show()
    }

    private fun acquireLand(gx: Int, gy: Int, method: String) {
        lifecycleScope.launch {
            try {
                val updatedUser = ApiClient.acquireLand(gx, gy, method)
                _userState.value = updatedUser
                val lands = ApiClient.getAllLands()
                _allLands.value = lands

                Toast.makeText(this@MainActivity, "Land acquired via $method!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Acquisition failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExchangeDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Pug Coins to exchange"
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Exchange Pug Coins")
            .setMessage("1 Pug Coin = 150 Pugbucks")
            .setView(container)
            .setPositiveButton("Exchange") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                if (amount == null || amount < 1.0) {
                    Toast.makeText(this, "Invalid amount. Must be at least 1.", Toast.LENGTH_LONG).show()
                } else {
                    exchangePugCoins(amount)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exchangePugCoins(amount: Double) {
        lifecycleScope.launch {
            try {
                val updatedUser = ApiClient.exchangePugCoins(amount)
                _userState.value = updatedUser
                Toast.makeText(this@MainActivity, "Exchanged $amount Pug Coins for ${amount * 150} Pugbucks!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Exchange failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun setupMap() {
        val styleUrl = "https://demotiles.maplibre.org/style.json"
        maplibreMap.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            addMapLayers(style)
            checkLocationPermissionAndEnableLocation(style)
            setupMapClickListener()
            maplibreMap.addOnCameraIdleListener { updateGridLayer() }
            updateGridLayer()
            updateAllLandsLayer()
        }
    }

    private fun addMapLayers(style: Style) {
        style.addSource(GeoJsonSource(ALL_LANDS_SOURCE_ID))
        style.addLayer(
            FillLayer(ALL_LANDS_FILL_LAYER_ID, ALL_LANDS_SOURCE_ID)
                .withProperties(
                    PropertyFactory.fillColor(
                        Expression.match(
                            Expression.get(IS_CURRENT_PLAYER_PROPERTY_KEY),
                            Expression.rgb(76, 175, 80),
                            Expression.stop(true, Expression.rgb(244, 67, 54))
                        )
                    ),
                    PropertyFactory.fillOpacity(0.5f)
                )
        )
        style.addLayer(SymbolLayer(ALL_LANDS_TEXT_LAYER_ID, ALL_LANDS_SOURCE_ID)
            .withProperties(PropertyFactory.textField("{${OWNER_NAME_PROPERTY_KEY}}"), PropertyFactory.textSize(12f), PropertyFactory.textColor(Color.WHITE), PropertyFactory.textAllowOverlap(true)))
        style.addSource(GeoJsonSource(PLAYER_RANGE_SOURCE_ID))
        style.addLayer(CircleLayer(PLAYER_RANGE_LAYER_ID, PLAYER_RANGE_SOURCE_ID)
            .withProperties(PropertyFactory.circleColor(Color.parseColor("#3300AAFF")), PropertyFactory.circleStrokeColor(Color.parseColor("#FF00AAFF")), PropertyFactory.circleStrokeWidth(2f)))
        style.addSource(GeoJsonSource(GRID_SOURCE_ID))
        style.addLayer(LineLayer(GRID_LAYER_ID, GRID_SOURCE_ID)
            .withProperties(PropertyFactory.lineWidth(1.0f), PropertyFactory.lineColor(Color.parseColor("#888888"))))
    }

    private fun getGridPolygonByCoords(gx: Int, gy: Int): Polygon {
        val corners = listOf(
            GridUtils.gridToLatLon(gx, gy), GridUtils.gridToLatLon(gx + 1, gy),
            GridUtils.gridToLatLon(gx + 1, gy + 1), GridUtils.gridToLatLon(gx, gy + 1),
            GridUtils.gridToLatLon(gx, gy)
        )
        return Polygon.fromLngLats(listOf(corners.map { Point.fromLngLat(it.longitude, it.latitude) }))
    }

    private fun updateAllLandsLayer() {
        if (!::maplibreMap.isInitialized || maplibreMap.style?.isFullyLoaded == false) return
        val currentUserId = ApiClient.currentAuthUser?.uid ?: return

        maplibreMap.style?.getSourceAs<GeoJsonSource>(ALL_LANDS_SOURCE_ID)?.let { source ->
            val features = _allLands.value.map { land ->
                val isOwner = land.ownerId == currentUserId
                val centerLatLng = GridUtils.gridToLatLon(land.gx + 0.5, land.gy + 0.5)

                val polygonFeature = Feature.fromGeometry(getGridPolygonByCoords(land.gx, land.gy))
                polygonFeature.addBooleanProperty(IS_CURRENT_PLAYER_PROPERTY_KEY, isOwner)

                val pointFeature = Feature.fromGeometry(Point.fromLngLat(centerLatLng.longitude, centerLatLng.latitude))
                pointFeature.addStringProperty(OWNER_NAME_PROPERTY_KEY, land.ownerName)

                listOf(polygonFeature, pointFeature)
            }.flatten()
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    private fun updateGridLayer() {
        if (!::maplibreMap.isInitialized || maplibreMap.style == null) return
        val visibleBounds = maplibreMap.projection.visibleRegion.latLngBounds
        if (maplibreMap.cameraPosition.zoom < 14.0) {
            maplibreMap.style?.getSourceAs<GeoJsonSource>(GRID_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val (minGx, minGy) = GridUtils.latLonToGrid(visibleBounds.southWest)
        val (maxGx, maxGy) = GridUtils.latLonToGrid(visibleBounds.northEast)
        val features = mutableListOf<Feature>()
        for (gx in minGx..maxGx) {
            val start = GridUtils.gridToLatLon(gx, minGy); val end = GridUtils.gridToLatLon(gx, maxGy)
            features.add(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(start.longitude, start.latitude), Point.fromLngLat(end.longitude, end.latitude)))))
        }
        for (gy in minGy..maxGy) {
            val start = GridUtils.gridToLatLon(minGx, gy); val end = GridUtils.gridToLatLon(maxGx, gy)
            features.add(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(start.longitude, start.latitude), Point.fromLngLat(end.longitude, end.latitude)))))
        }
        maplibreMap.style?.getSourceAs<GeoJsonSource>(GRID_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        val locationComponent: LocationComponent = maplibreMap.locationComponent
        val options = LocationComponentActivationOptions.builder(this, style).build()
        locationComponent.activateLocationComponent(options)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS
        locationComponent.locationEngine?.getLastLocation(locationCallback)
        locationComponent.locationEngine?.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun updatePlayerRangeLayer() {
        val location = playerLocation ?: return
        if (!::maplibreMap.isInitialized) return

        val currentRange = getCurrentRangeMeters()

        maplibreMap.style?.getSourceAs<GeoJsonSource>(PLAYER_RANGE_SOURCE_ID)?.let { source ->
            source.setGeoJson(createCirclePolygon(location, currentRange))
        }
    }

    private fun setupMapClickListener() {
        maplibreMap.addOnMapClickListener { latLng ->
            val playerLoc = playerLocation ?: run {
                Toast.makeText(this, "Still acquiring your location...", Toast.LENGTH_SHORT).show()
                return@addOnMapClickListener true
            }
            val (gx, gy) = GridUtils.latLonToGrid(latLng)

            if (_allLands.value.any { it.gx == gx && it.gy == gy }) {
                Toast.makeText(this, "This land is already owned!", Toast.LENGTH_SHORT).show()
                return@addOnMapClickListener true
            }

            val distance = GridUtils.distanceInMeters(playerLoc, latLng)
            if (distance > getCurrentRangeMeters()) {
                Toast.makeText(this, "This land is out of your range!", Toast.LENGTH_SHORT).show()
                return@addOnMapClickListener true
            }
            showAcquireLandDialog(gx, gy)
            true
        }
    }

    private fun showBulkClaimDialog() {
        val playerLoc = playerLocation ?: run {
            Toast.makeText(this, "Acquiring your location, try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentVouchers = _userState.value?.landVouchers ?: 0
        if (currentVouchers <= 0) {
            Toast.makeText(this, "You don't have any vouchers to bulk claim!", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Vouchers to spend (You have $currentVouchers)"
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Bulk Claim Land in Range")
            .setMessage("Enter how many vouchers you want to use to claim nearby plots.")
            .setView(container)
            .setPositiveButton("Claim") { _, _ ->
                val amount = input.text.toString().toIntOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Invalid amount.", Toast.LENGTH_SHORT).show()
                } else if (amount > currentVouchers) {
                    Toast.makeText(this, "You don't have that many vouchers.", Toast.LENGTH_SHORT).show()
                } else {
                    handleBulkClaimInRange(amount, playerLoc)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleBulkClaimInRange(amountToClaim: Int, centerLocation: LatLng) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Finding Land...")
            .setMessage("Searching for $amountToClaim unowned plots in your area.")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val ownedLandCoords = _allLands.value.map { it.gx to it.gy }.toSet()
                val nearbyUnownedPlots = mutableListOf<Map<String, Int>>()
                val (centerGx, centerGy) = GridUtils.latLonToGrid(centerLocation)

                val currentRange = getCurrentRangeMeters()

                var searchRadius = 0
                while (nearbyUnownedPlots.size < amountToClaim && searchRadius < 50) {
                    for (dx in -searchRadius..searchRadius) {
                        for (dy in -searchRadius..searchRadius) {
                            if (abs(dx) < searchRadius && abs(dy) < searchRadius) continue

                            val gx = centerGx + dx
                            val gy = centerGy + dy
                            val plotCoords = gx to gy

                            if (ownedLandCoords.contains(plotCoords) || nearbyUnownedPlots.any { it["gx"] == gx && it["gy"] == gy }) {
                                continue
                            }

                            val plotCenterLatLng = GridUtils.gridToLatLon(gx + 0.5, gy + 0.5)
                            if (GridUtils.distanceInMeters(centerLocation, plotCenterLatLng) <= currentRange) {
                                nearbyUnownedPlots.add(mapOf("gx" to gx, "gy" to gy))
                                if (nearbyUnownedPlots.size >= amountToClaim) break
                            }
                        }
                        if (nearbyUnownedPlots.size >= amountToClaim) break
                    }
                    searchRadius++
                }

                if (nearbyUnownedPlots.size < amountToClaim) {
                    throw Exception("Could not find enough unowned land in range. Found ${nearbyUnownedPlots.size}. Move to a new area.")
                }

                val updatedUser = ApiClient.bulkClaim(nearbyUnownedPlots)

                _userState.value = updatedUser

                val lands = ApiClient.getAllLands()
                _allLands.value = lands

                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Successfully claimed $amountToClaim new plots nearby!", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            if (::maplibreMap.isInitialized && maplibreMap.style?.isFullyLoaded == true) {
                maplibreMap.style?.let { enableLocationComponent(it) }
            }
        } else {
            Toast.makeText(this, "Location permission is required for Puglands.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkLocationPermissionAndEnableLocation(style: Style) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableLocationComponent(style)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun formatCurrency(value: Double): String {
        return String.format(Locale.US, "%.11f", max(0.0, value)).trimEnd('0').trimEnd('.')
    }

    private fun createCirclePolygon(center: LatLng, radiusInMeters: Double): Polygon {
        val points = mutableListOf<Point>()
        val distanceX = radiusInMeters / (111320.0 * Math.cos(Math.toRadians(center.latitude)))
        val distanceY = radiusInMeters / 110540.0
        for (i in 0..360 step 10) {
            val angleRad = Math.toRadians(i.toDouble())
            val lon = center.longitude + distanceX * Math.cos(angleRad)
            val lat = center.latitude + distanceY * Math.sin(angleRad)
            points.add(Point.fromLngLat(lon, lat))
        }
        return Polygon.fromLngLats(listOf(points))
    }

    private fun saveUserProgress() {
        val user = _userState.value ?: return

        lifecycleScope.launch {
            try {
                ApiClient.updateUser(mapOf("balance" to user.balance, "pug_coins" to user.pugCoins))
            } catch (e: Exception) {
                // Handle failure to save data
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
        mediaPlayer?.start()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (::maplibreMap.isInitialized && maplibreMap.locationComponent.isLocationComponentActivated) {
            maplibreMap.locationComponent.locationEngine?.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
        mediaPlayer?.start()

        forceRefreshUserData()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (::maplibreMap.isInitialized && maplibreMap.locationComponent.isLocationComponentActivated) {
            maplibreMap.locationComponent.locationEngine?.removeLocationUpdates(locationCallback)
        }
        mediaPlayer?.pause()
    }

    // BUG FIX: The onStop method no longer needs to save progress, as the server does it automatically.
    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
        incomeBoostTimerJob?.cancel()
        rangeBoostTimerJob?.cancel()
        gameStatePollingJob?.cancel() // Stop polling when the app is backgrounded
        // No longer need to call saveUserProgress()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::maplibreMap.isInitialized) {
            binding.mapView.onSaveInstanceState(outState)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
            binding.mapView.onDestroy()
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }
}