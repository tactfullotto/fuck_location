package com.example.xposedgpshook.ui

import android.content.Context

class LocationStorage(context: Context) {

    private val prefs = context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)
    private val locationsKey = "locations"

    fun getLocations(): List<LocationItem> {
        val locationsString = prefs.getString(locationsKey, null) ?: return emptyList()

        return locationsString.split(";")
            .filter { it.isNotEmpty() }
            .mapNotNull { locationStr ->
                try {
                    val parts = locationStr.split(",")
                    if (parts.size >= 3) {
                        val name = parts[0]
                        val latitude = parts[1].toDouble()
                        val longitude = parts[2].toDouble()
                        // 如果有 ID 则使用，否则生成新的
                        val id = if (parts.size >= 4) parts[3] else System.currentTimeMillis().toString()
                        LocationItem(name, latitude, longitude, id)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
    }

    fun addLocation(location: LocationItem) {
        val locations = getLocations().toMutableList()
        locations.add(location)
        saveLocations(locations)
    }

    fun removeLocation(location: LocationItem) {
        val locations = getLocations().toMutableList()
        locations.removeAll { it.id == location.id }
        saveLocations(locations)
    }

    fun updateLocation(oldLocation: LocationItem, newLocation: LocationItem) {
        val locations = getLocations().toMutableList()
        val index = locations.indexOfFirst { it.id == oldLocation.id }
        if (index != -1) {
            // 保持相同的 ID
            locations[index] = LocationItem(
                newLocation.name,
                newLocation.latitude,
                newLocation.longitude,
                oldLocation.id
            )
            saveLocations(locations)
        }
    }

    private fun saveLocations(locations: List<LocationItem>) {
        val locationsString = locations.joinToString(";") { "${it.name},${it.latitude},${it.longitude},${it.id}" }
        prefs.edit().putString(locationsKey, locationsString).apply()
    }
}
