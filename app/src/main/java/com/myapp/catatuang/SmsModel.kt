package com.myapp.catatuang

data class SmsModel(
    val sender: String,
    val body: String,
    val amount: Double,
    val date: Long
)
