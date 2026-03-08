package com.akocis.babysleeptracker.model

enum class NoiseType(val label: String) {
    WHITE("White"),
    PINK("Pink"),
    BROWN("Brown"),
    BLUE("Blue"),
    VIOLET("Violet"),
    GRAY("Gray");

    companion object {
        fun fromString(value: String): NoiseType? = when (value.uppercase()) {
            "WHITE" -> WHITE
            "PINK" -> PINK
            "BROWN" -> BROWN
            "BLUE" -> BLUE
            "VIOLET" -> VIOLET
            "GRAY" -> GRAY
            else -> null
        }
    }
}
