package com.akocis.babysleeptracker.model

enum class DiaperType(val label: String) {
    PEE("Pee"),
    POO("Poo"),
    PEEPOO("Pee + Poo");

    companion object {
        fun fromString(value: String): DiaperType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
