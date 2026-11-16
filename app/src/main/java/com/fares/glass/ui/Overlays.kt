package com.fares.glass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ControlItem(val icon: ImageVector, val title: String, val subtitle: String?)

@Composable
fun menuItems() = listOf(
    ControlItem(Icons.Rounded.OpenWith, stringResource(R.string.menu_move), null),
    ControlItem(Icons.Rounded.VisibilityOff, stringResource(R.string.menu_hide), null),
    ControlItem(Icons.Rounded.Info, stringResource(R.string.menu_info), null),
)

/** Text style in the UI font (Inter, or the one supplied in /sdcard/Glass/fonts). */
fun txt(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = Typo.family)

private val white = Color.White

/** Touch screens: a tap (and optionally a long press) on this element. Remote control navigation is unaffected. */
fun Modifier.tappable(onLong: (() -> Unit)? = null, onTap: () -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLong)
    pointerInput(onLong != null) {
        detectTapGestures(onLongPress = if (onLong != null) ({ long?.invoke() }) else null, onTap = { tap() })
    }
}

private val ink = Color(0xFF111114)

/** tvOS button: translucent at rest, a bright white plate with dark text when focused. */
@Composable
private fun GlassButton(item: ControlItem, focused: Boolean, modifier: Modifier) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 600f), label = "btn")
    val fg = lerpColor(white, ink, f)
    Row(
        modifier
            .graphicsLayer { scaleX = 1f + 0.05f * f; scaleY = 1f + 0.05f * f }
            .background(lerpColor(white.copy(alpha = 0.10f), white.copy(alpha = 0.94f), f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(rememberVectorPainter(item.icon), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(fg))
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(item.title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1)
            item.subtitle?.let {
                BasicText(it, style = txt(fg.copy(alpha = 0.65f), 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ContextMenu(backdrop: Backdrop, title: String, items: List<ControlItem>, focus: Int, modifier: Modifier, onPick: (Int) -> Unit = {}) {
    Glass(backdrop, modifier.width(260.dp), RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(12.dp)) {
            BasicText(
                title, Modifier.padding(start = 10.dp, top = 4.dp, bottom = 8.dp),
                style = txt(white.copy(alpha = 0.7f), 13.sp, FontWeight.SemiBold), maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            items.forEachIndexed { i, item ->
                GlassButton(item, i == focus, Modifier.fillMaxWidth().tappable { onPick(i) })
                if (i < items.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun HintPill(backdrop: Backdrop, text: String, modifier: Modifier) {
    Glass(backdrop, modifier, RoundedCornerShape(50)) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) {
            BasicText(text, style = txt(white, 14.sp, FontWeight.Medium))
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** Current time, refreshed every second while the element is shown. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

private val headerShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(0f, 3f), 18f)

/** Time and date in the device's language and 12/24-hour setting, recomputed every minute. */
@Composable
fun rememberClock(now: Long): Pair<String, String> {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    return remember(now / 60000, locale) {
        val h24 = android.text.format.DateFormat.is24HourFormat(context)
        val time = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", locale).format(Date(now))
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")
        time to SimpleDateFormat(pattern, locale).format(Date(now)).replaceFirstChar { it.titlecase(locale) }
    }
}

/** Home header: greeting, large time and date. */
@Composable
fun HomeHeader(name: String, modifier: Modifier) {
    val now = rememberNow()
    val hour = remember(now / 60000) { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val (plain, named) = when (hour) {
        in 5..11 -> R.string.greeting_morning to R.string.greeting_morning_name
        in 12..17 -> R.string.greeting_afternoon to R.string.greeting_afternoon_name
        else -> R.string.greeting_evening to R.string.greeting_evening_name
    }
    val greeting = if (name.isBlank()) stringResource(plain) else stringResource(named, name)
    val (time, date) = rememberClock(now)
    Column(modifier) {
        BasicText(greeting, style = txt(white, 17.sp, FontWeight.SemiBold).copy(shadow = headerShadow))
        BasicText(time, style = txt(white, 64.sp, FontWeight.Bold).copy(shadow = headerShadow, letterSpacing = (-1).sp))
        BasicText(date, style = txt(white.copy(alpha = 0.9f), 18.sp, FontWeight.Medium).copy(shadow = headerShadow))
    }
}

@Composable
fun topItems() = listOf(
    ControlItem(Icons.Rounded.Wifi, "Wi-Fi", null),
    ControlItem(Icons.Rounded.Bluetooth, "Bluetooth", null),
    ControlItem(Icons.Rounded.Settings, stringResource(R.string.top_settings), null),
)

/** Top-right buttons: Liquid Glass pills with a white plate when focused. */
@Composable
fun TopBar(backdrop: Backdrop, items: List<ControlItem>, focus: Int, modifier: Modifier, onPick: (Int) -> Unit = {}) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { i, item ->
            val f by animateFloatAsState(if (i == focus) 1f else 0f, spring(stiffness = 600f), label = "top")
            val fg = lerpColor(white, ink, f)
            Glass(
                backdrop,
                Modifier.graphicsLayer { scaleX = 1f + 0.08f * f; scaleY = 1f + 0.08f * f }.tappable { onPick(i) },
                RoundedCornerShape(50),
                tint = Color.White.copy(alpha = 0.10f),
            ) {
                Row(
                    Modifier
                        .background(white.copy(alpha = 0.94f * f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(rememberVectorPainter(item.icon), null, Modifier.size(17.dp), colorFilter = ColorFilter.tint(fg))
                    Spacer(Modifier.width(7.dp))
                    BasicText(item.title, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1)
                }
            }
        }
    }
}
