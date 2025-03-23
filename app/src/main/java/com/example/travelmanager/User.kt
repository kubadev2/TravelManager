package com.example.travelmanager

data class User(
    val uid: String = "",
    val email: String = "",
    val friends: List<String> = emptyList()
)
