package com.akocis.babysleeptracker.model

enum class NoiseType(val label: String, val description: String) {
    WHITE("White", "Equal energy, bright hiss"),
    PINK("Pink", "Softer, balanced warmth"),
    BROWN("Brown", "Deep, low rumble"),
    GRAY("Gray", "Perceptually even"),
    BLUE("Blue", "Bright, airy hiss"),
    VIOLET("Violet", "Sharp, high shimmer"),
    RAIN("Rain", "Gentle rainfall"),
    STORM("Storm", "Rain with thunder");

    companion object {
        fun fromString(value: String): NoiseType? = when (value.uppercase()) {
            "WHITE" -> WHITE
            "PINK" -> PINK
            "BROWN" -> BROWN
            "BLUE" -> BLUE
            "VIOLET" -> VIOLET
            "GRAY" -> GRAY
            "RAIN" -> RAIN
            "STORM" -> STORM
            else -> null
        }
    }
}
