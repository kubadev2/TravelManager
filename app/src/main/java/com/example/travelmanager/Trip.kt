package com.example.travelmanager

data class Trip(
    val tripId: String = "",
    val destination: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val description: String = "",
    val imageUrl: String = ""
)
