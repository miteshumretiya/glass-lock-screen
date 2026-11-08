package com.mitesh.glasslock.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mitesh.glasslock.R

/** Inter: a close, openly licensed stand-in for Apple's San Francisco type. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Cap height of Inter as a fraction of the font size (digits sit at cap height). */
const val INTER_CAP_HEIGHT = 0.7275f
