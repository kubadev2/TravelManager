package com.example.travelmanager

import java.text.SimpleDateFormat
import java.util.*

data class Trip(
    val tripId: String = "", // Unikalne ID
    val userId: String = "",
    val departurePlace: String = "",
    val startDate: String = "",
    val endDate: String = "" // Dodano pole endDate
)


