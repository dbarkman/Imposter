package com.redmesastudios.imposter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.redmesastudios.imposter.R

// A dark plum ground, red for the imposters, mint for the crew and gold for focus.
val Night = Color(0xFF17121F)
val Panel = Color(0xFF241C30)
val Line = Color(0xFF33284A)
val LineBright = Color(0xFF4A3B66)
val Text = Color(0xFFF7F2FF)
val TextSoft = Color(0xFFC2B6DA)
val TextFaint = Color(0xFF8B7FA6)
val Gold = Color(0xFFFFD25E)
val Red = Color(0xFFFF4D5E)
val Mint = Color(0xFF4FE0B0)

// Players' colors, in the order the phone page offers them (the same in every game on the
// Game Bridge, since the household's players are shared).
val PlayerColors = listOf(
    Color(0xFF4DB5FF), Color(0xFFFF7A66), Mint, Gold, Color(0xFFB79BFF), Color(0xFFFF8FC7), Color(0xFFB8E55C), Color(0xFFFFA44F),
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(res, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Bricolage = FontFamily(listOf(600, 700, 800).map { variable(R.font.bricolage, it) })
val Figtree = FontFamily(listOf(400, 500, 600, 700, 800).map { variable(R.font.figtree, it) })

// Sizes for 1920x1080 px at 320 dpi, so 1 sp is 2 px.
object Type {
    val huge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 66.sp, letterSpacing = (-2).sp, color = Text)
    val title = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1).sp, color = Text)
    val kicker = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp, color = Gold)
    val body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = Text)
    val name = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Text)
    val soft = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextSoft)
    val label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp, color = TextSoft)
    val chip = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val button = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
}
