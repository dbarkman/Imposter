package com.redmesastudios.imposter

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.redmesastudios.imposter.ui.Gold
import com.redmesastudios.imposter.ui.LineBright
import com.redmesastudios.imposter.ui.Night
import com.redmesastudios.imposter.ui.Panel
import com.redmesastudios.imposter.ui.Text
import com.redmesastudios.imposter.ui.Type

// Focus, as the remote moves it.
@Composable
fun rememberFocus(): Pair<Boolean, Modifier> {
    var focused by remember { mutableStateOf(false) }
    return focused to Modifier.onFocusChanged { focused = it.isFocused }
}

fun Modifier.pressable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

// A rounded button: gold when focused, filled softly when chosen, outlined otherwise.
@Composable
fun Pill(label: String, modifier: Modifier = Modifier, selected: Boolean = false, large: Boolean = false, onClick: () -> Unit) {
    val (focused, focus) = rememberFocus()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(200), label = "pillScale")
    val fill by animateColorAsState(
        when {
            focused -> Gold
            selected -> Color(0xFF4DB5FF)
            else -> Panel
        },
        tween(200),
        label = "pillFill",
    )
    val ink by animateColorAsState(if (focused || selected) Night else Text, tween(200), label = "pillInk")
    val border by animateColorAsState(if (focused || selected) Color.Transparent else LineBright, tween(200), label = "pillBorder")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(fill, CircleShape)
            .border(1.dp, border, CircleShape)
            .then(focus)
            .pressable(onClick)
            .padding(horizontal = if (large) 26.dp else 14.dp, vertical = if (large) 12.dp else 6.dp),
    ) {
        BasicText(label, style = if (large) Type.button else Type.chip, color = { ink }, maxLines = 1, softWrap = false)
    }
}

// A quiet label in a rounded outline.
@Composable
fun Tag(label: String, modifier: Modifier = Modifier, color: Color = Text, fill: Color = Panel) {
    Box(modifier.background(fill, CircleShape).border(1.dp, LineBright, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp)) {
        BasicText(label, style = Type.chip.copy(fontSize = 12.sp, color = color), maxLines = 1, softWrap = false)
    }
}

// A player's colored circle with their first letter.
@Composable
fun PlayerBadge(name: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        BasicText(name.take(1).uppercase(), style = Type.button.copy(fontSize = (size.value * 0.45f).sp, color = Night))
    }
}

@Composable
fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    Canvas(modifier) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) drawRect(Night, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

@Composable
fun HintRow(vararg hints: String) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
        hints.forEach { BasicText(it, style = Type.soft.copy(fontSize = 13.sp)) }
    }
}
