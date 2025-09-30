package com.puglands.game.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.puglands.game.R
import com.puglands.game.data.database.Land
import java.util.*

class PlotAdapter(private val onPlotClick: (Land) -> Unit) : ListAdapter<Land, PlotAdapter.PlotViewHolder>(PlotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plot, parent, false)
        return PlotViewHolder(view, onPlotClick)
    }

    override fun onBindViewHolder(holder: PlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlotViewHolder(itemView: View, private val onPlotClick: (Land) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val coordinates: TextView = itemView.findViewById(R.id.plotCoordinates)
        private val incomePrompt: TextView = itemView.findViewById(R.id.plotIncomePrompt)

        fun bind(land: Land) {
            coordinates.text = "Grid: (${land.gx}, ${land.gy})"
            incomePrompt.text = "Tap for Stats"

            itemView.setOnClickListener {
                onPlotClick(land)
            }
        }
    }
}

class PlotDiffCallback : DiffUtil.ItemCallback<Land>() {
    override fun areItemsTheSame(oldItem: Land, newItem: Land): Boolean {
        // Use coordinates as the unique identifier since there's no ID from Firestore
        return oldItem.gx == newItem.gx && oldItem.gy == newItem.gy
    }

    override fun areContentsTheSame(oldItem: Land, newItem: Land): Boolean {
        return oldItem == newItem
    }
}