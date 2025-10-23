package com.example.xposedgpshook.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.xposedgpshook.R

class LocationAdapter(
    private val locations: MutableList<LocationItem>,
    private val onItemClick: (LocationItem) -> Unit,
    private val onEditClick: (LocationItem, Int) -> Unit,
    private val onDeleteClick: (LocationItem) -> Unit
) : RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val location = locations[position]
        holder.bind(location, position, onItemClick, onEditClick, onDeleteClick)
    }

    override fun getItemCount(): Int = locations.size

    fun addLocation(location: LocationItem) {
        locations.add(location)
        notifyItemInserted(locations.size - 1)
    }

    fun removeLocation(location: LocationItem) {
        val position = locations.indexOf(location)
        if (position > -1) {
            locations.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateLocation(position: Int, updatedLocation: LocationItem) {
        if (position in locations.indices) {
            locations[position].name = updatedLocation.name
            locations[position].latitude = updatedLocation.latitude
            locations[position].longitude = updatedLocation.longitude
            notifyItemChanged(position)
        }
    }

    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_location_name)
        private val coordsTextView: TextView = itemView.findViewById(R.id.tv_location_coords)
        private val editButton: Button = itemView.findViewById(R.id.btn_edit_location)
        private val deleteButton: Button = itemView.findViewById(R.id.btn_delete_location)

        fun bind(
            location: LocationItem,
            position: Int,
            onItemClick: (LocationItem) -> Unit,
            onEditClick: (LocationItem, Int) -> Unit,
            onDeleteClick: (LocationItem) -> Unit
        ) {
            nameTextView.text = location.name
            coordsTextView.text = "Lat: ${location.latitude}, Lng: ${location.longitude}"
            itemView.setOnClickListener { onItemClick(location) }
            editButton.setOnClickListener { onEditClick(location, position) }
            deleteButton.setOnClickListener { onDeleteClick(location) }
        }
    }
}
