package com.myapp.centsibleai

data class SmsModel(
    val sender: String,
    val body: String,
    val amount: Double,
    val date: Long,
    val type: Int
)
