package com.spacebrowser.ui.theme

import androidx.compose.ui.graphics.Color

// Deep-space surfaces
val SpaceBlack = Color(0xFF050510)      // window / AMOLED-adjacent base
val SpaceDeep = Color(0xFF0B0E1A)       // dark background
val SpaceSurface = Color(0xFF12162A)    // dark surface
val SpaceSurfaceHigh = Color(0xFF1A2038)
val Amoled = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0F)

// Light "daylight over the stratosphere"
val DayBackground = Color(0xFFF3F4FB)
val DaySurface = Color(0xFFFFFFFF)
val DaySurfaceHigh = Color(0xFFE9EAF6)
val DayOnSurface = Color(0xFF191C2B)

val StarWhite = Color(0xFFE9E4FF)
val MutedStar = Color(0xFF8B90B0)

data class Accent(val name: String, val color: Color, val secondary: Color)

/** The RGB accent picker draws from this named palette. */
val Accents: List<Accent> = listOf(
    Accent("Nebula Violet", Color(0xFF8B5CF6), Color(0xFF22D3EE)),
    Accent("Aurora Cyan", Color(0xFF22D3EE), Color(0xFF8B5CF6)),
    Accent("Pulsar Pink", Color(0xFFEC4899), Color(0xFF8B5CF6)),
    Accent("Star Gold", Color(0xFFF59E0B), Color(0xFFEC4899)),
    Accent("Comet Green", Color(0xFF34D399), Color(0xFF22D3EE)),
    Accent("Ion Blue", Color(0xFF60A5FA), Color(0xFF34D399)),
    Accent("Supernova Red", Color(0xFFF87171), Color(0xFFF59E0B)),
    Accent("Quasar Purple", Color(0xFFA78BFA), Color(0xFFEC4899)),
)

fun accentAt(index: Int): Accent = Accents.getOrElse(index) { Accents.first() }
