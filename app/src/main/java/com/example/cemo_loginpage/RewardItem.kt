package com.example.cemo_loginpage

data class RewardItem(
    val docId  : String,
    val name   : String,
    val description: String,
    val points : Int,
    val stock  : Int,
    val claimed: Int,
    val expiry : String
)