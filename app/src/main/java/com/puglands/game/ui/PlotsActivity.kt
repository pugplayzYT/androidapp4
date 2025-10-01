package com.puglands.game.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.puglands.game.api.ApiClient
import com.puglands.game.data.database.Land
import com.puglands.game.databinding.ActivityPlotsBinding
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.max

class PlotsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlotsBinding
    // Firebase components removed
    private lateinit var plotAdapter: PlotAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlotsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadPlotsData()
    }

    private fun setupRecyclerView() {
        plotAdapter = PlotAdapter(::showPlotInfoDialog)
        binding.plotsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.plotsRecyclerView.adapter = plotAdapter
    }

    private fun loadPlotsData() {
        lifecycleScope.launch {
            val userId = ApiClient.currentAuthUser?.uid ?: run {
                binding.totalIncomeTextView.text = "Error: Not logged in."
                return@launch
            }
            try {
                // API Call to Flask server to get user's lands
                val lands = ApiClient.getUserLands(userId)

                plotAdapter.submitList(lands)

                val totalPPS = lands.sumOf { it.pps }
                val formattedTotal = formatCurrency(totalPPS)
                binding.totalIncomeTextView.text = "Total Income:\n$formattedTotal Pugbucks/sec"
            } catch (e: Exception) {
                binding.totalIncomeTextView.text = "Could not load plot data: ${e.message}"
            }
        }
    }

    private fun showPlotInfoDialog(land: Land) {
        val formattedPPS = formatCurrency(land.pps)
        val userName = ApiClient.currentAuthUser?.name ?: "Unknown"

        val detailedMessage = "Owner: $userName\n" +
                "Grid: (${land.gx}, ${land.gy})\n" +
                "Income: $formattedPPS PB/sec"

        AlertDialog.Builder(this)
            .setTitle("Plot Stats")
            .setMessage(detailedMessage)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatCurrency(value: Double): String {
        return String.format(Locale.US, "%.11f", max(0.0, value)).trimEnd('0').trimEnd('.')
    }
}