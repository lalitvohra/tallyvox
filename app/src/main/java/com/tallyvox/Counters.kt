package com.tallyvox

data class Counters(
    val primary: Int = 0,
    val secondary: Int = 0,
    val interval: Int = 100,
    val isVoiceMode: Boolean = false
)
