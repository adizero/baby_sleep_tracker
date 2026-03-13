package com.akocis.babysleeptracker.model

enum class BabySex(val label: String) {
    BOY("Boy"),
    GIRL("Girl");

    companion object {
        fun fromString(value: String): BabySex? = entries.find { it.name == value.uppercase() }
    }
}
