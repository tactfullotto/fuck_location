package com.example.xposedgpshook.ui

data class LocationItem(
    var name: String,
    var latitude: Double,
    var longitude: Double,
    val id: String = System.currentTimeMillis().toString()
)
