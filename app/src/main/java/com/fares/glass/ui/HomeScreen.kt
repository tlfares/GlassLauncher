package com.fares.glass.ui

import android.graphics.Rect
import android.view.KeyEvent as AndroidKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fares.glass.R
import com.fares.glass.data.AppEntry
import com.fares.glass.data.AppRepository

sealed interface Command {
    data object Settings : Command
    data object Network : Command
    data object Bluetooth : Command
    data object Apps : Command
    data object NextWallpaper : Command
    data class AppInfo(val pkg: String) : Command
}

private const val LONG_PRESS_MS = 450L
private val MenuHeight = 196.dp
private val MenuWidth = 260.dp
private val DockInset = 20.dp
private val DockTop = 18.dp
/** Covers the row and the focus zoom (no names under dock icons). */
private val DockHeight = Metrics.TileH + 36.dp

@Composable
fun HomeScreen(
    repo: AppRepository,
    state: HomeState,
    backdrop: Backdrop,
    wallpaper: ImageBitmap?,
    wallpaperName: String,
    cc: ControlCenter,
    onLaunch: (AppEntry, Rect) -> Unit,
    onCommand: (Command) -> Unit,
) {
    val apps = repo.apps
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // Wi-Fi password entry finished: the remote drives the home screen again.
    LaunchedEffect(cc.typing) { if (!cc.typing) focusRequester.requestFocus() }
    LaunchedEffect(apps.size) {
        if (apps.isNotEmpty() && state.focus > apps.lastIndex) state.focus = apps.lastIndex
    }

    val row = state.row
    val scrollTarget = if (row == 0) 0f else (Metrics.TopRowY + Metrics.Pitch * row - Metrics.GridRowY).value
    val scroll = animateFloatAsState(scrollTarget, spring(dampingRatio = 1f, stiffness = 210f), label = "scroll")
    val inGrid = row > 0 || state.mode == Mode.Control
    val blur by animateFloatAsState(if (inGrid) 1f else 0f, tween(450), label = "blur")
    val dim by animateFloatAsState(
        if (state.mode == Mode.Control || state.mode == Mode.Menu) 0.3f else 0f, tween(250), label = "dim",
    )
    val focused = apps.getOrNull(state.focus)
    val ambience = if (repo.topShelf && row == 0 && state.mode == Mode.Browse) {
        focused?.let { repo.visuals[it.pkg]?.ambience }
    } else null

    val menu = menuItems()
    val top = topItems()

    fun tileX(i: Int) = Metrics.Margin + (Metrics.TileW + Metrics.Gap) * (i % Metrics.COLS)
    fun tileY(i: Int) = (Metrics.TopRowY + Metrics.Pitch * (i / Metrics.COLS)).value - scrollTarget

    fun launchAt(i: Int) {
        val entry = apps.getOrNull(i) ?: return
        val r = with(density) {
            val x = tileX(i).roundToPx()
            val y = tileY(i).dp.roundToPx()
            Rect(x, y, x + Metrics.TileW.roundToPx(), y + Metrics.TileH.roundToPx())
        }
        onLaunch(entry, r)
    }

    fun launchFocused() = launchAt(state.focus)

    fun move(dx: Int, dy: Int) {
        when (state.mode) {
            Mode.Browse -> {
                if (dy < 0 && row == 0) {
                    // Above the dock: the glass buttons at the top right.
                    state.topIndex = top.lastIndex
                    state.mode = Mode.Top
                    return
                }
                HomeState.target(state.focus, dx, dy, apps.size)?.let { state.focus = it }
                state.bump(dx, dy)
            }
            Mode.Top -> when {
                dy > 0 -> state.mode = Mode.Browse
                dx != 0 -> state.topIndex = (state.topIndex + dx).coerceIn(0, top.lastIndex)
            }
            Mode.Jiggle -> HomeState.target(state.focus, dx, dy, apps.size)?.let {
                repo.move(state.focus, it)
                state.focus = it
            }
            Mode.Menu -> state.menuIndex = (state.menuIndex + dy).coerceIn(0, menu.lastIndex)
            Mode.Control -> Unit // handled by the Control Center
        }
    }

    fun select() {
        when (state.mode) {
            Mode.Browse -> launchFocused()
            Mode.Top -> {
                // The top buttons open the Control Center straight on the matching page.
                cc.open(when (state.topIndex) { 0 -> CcPage.Wifi; 1 -> CcPage.Bluetooth; else -> CcPage.Main })
                state.mode = Mode.Control
            }
            Mode.Jiggle -> {
                repo.saveOrder()
                state.mode = Mode.Browse
            }
            Mode.Menu -> {
                val entry = focused
                state.mode = Mode.Browse
                if (entry != null) when (state.menuIndex) {
                    0 -> state.mode = Mode.Jiggle
                    1 -> repo.hide(entry.pkg)
                    2 -> onCommand(Command.AppInfo(entry.pkg))
                }
            }
            Mode.Control -> Unit
        }
    }

    fun longPress() {
        if (state.mode == Mode.Browse && focused != null) {
            state.menuIndex = 0
            state.mode = Mode.Menu
        }
    }

    // Touch screens: tap opens an app, long press opens its menu.
    fun tapTile(i: Int) {
        when (state.mode) {
            Mode.Jiggle -> { repo.saveOrder(); state.mode = Mode.Browse }
            Mode.Control, Mode.Menu -> Unit
            else -> { state.mode = Mode.Browse; launchAt(i); state.focus = i }
        }
    }

    fun longTile(i: Int) {
        if (state.mode == Mode.Browse || state.mode == Mode.Top) {
            state.focus = i
            state.menuIndex = 0
            state.mode = Mode.Menu
        }
    }

    fun back() {
        when (state.mode) {
            Mode.Menu, Mode.Control, Mode.Top -> state.mode = Mode.Browse
            Mode.Jiggle -> { repo.saveOrder(); state.mode = Mode.Browse }
            Mode.Browse -> if (row > 0) state.focus %= Metrics.COLS
        }
    }

    fun menuKey() {
        state.mode = when (state.mode) {
            Mode.Browse, Mode.Top -> { cc.open(CcPage.Main); Mode.Control }
            Mode.Jiggle -> { repo.saveOrder(); Mode.Browse }
            else -> Mode.Browse
        }
    }

    fun onKey(event: KeyEvent): Boolean {
        val e = event.nativeKeyEvent
        if (state.mode == Mode.Control) return cc.onKey(e)
        val down = e.action == AndroidKey.ACTION_DOWN
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER,
            AndroidKey.KEYCODE_BUTTON_A -> {
                if (down) {
                    if (e.repeatCount == 0) {
                        state.centerDownAt = e.eventTime
                        state.longFired = false
                        state.pressed = state.mode == Mode.Browse
                    } else if (!state.longFired && state.centerDownAt >= 0 && e.eventTime - state.centerDownAt > LONG_PRESS_MS) {
                        state.longFired = true
                        state.pressed = false
                        longPress()
                    }
                } else {
                    state.pressed = false
                    // A release without a press (OK held in the app we just left) is ignored.
                    if (state.centerDownAt >= 0 && !state.longFired) select()
                    state.centerDownAt = -1
                }
                return true
            }
            AndroidKey.KEYCODE_DPAD_LEFT -> if (down) move(-1, 0)
            AndroidKey.KEYCODE_DPAD_RIGHT -> if (down) move(1, 0)
            AndroidKey.KEYCODE_DPAD_UP -> if (down) move(0, -1)
            AndroidKey.KEYCODE_DPAD_DOWN -> if (down) move(0, 1)
            AndroidKey.KEYCODE_BACK -> if (down && e.repeatCount == 0) back()
            AndroidKey.KEYCODE_MENU -> if (down && e.repeatCount == 0) menuKey()
            AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE -> if (down && state.mode == Mode.Jiggle) {
                state.menuIndex = 0
                state.mode = Mode.Menu
            }
            else -> return false
        }
        return true
    }

    val moveNow by rememberUpdatedState<(Int, Int) -> Unit>(::move)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { backdrop.screen = it }
            .pointerInput(Unit) {
                // Swiping the grid moves one row at a time. Watched before the tiles, which keep their taps.
                val step = 56.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var travelled = 0f
                    var lastY = down.position.y
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        travelled += change.position.y - lastY
                        lastY = change.position.y
                        while (kotlin.math.abs(travelled) > step) {
                            val up = travelled < 0
                            travelled += if (up) step else -step
                            if (state.mode == Mode.Browse && (up || state.row > 0)) moveNow(0, if (up) 1 else -1)
                        }
                    }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent(::onKey),
    ) {
        // Background: sharp at the top of the home screen, blurred and dimmed once you move into the grid.
        Crossfade(wallpaper, animationSpec = tween(700), label = "wallpaper") { img ->
            if (img != null) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
            }
        }
        backdrop.frosted?.let {
            Image(
                it, null, Modifier.fillMaxSize().graphicsLayer { alpha = blur },
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low,
            )
        }
        Crossfade(ambience, animationSpec = tween(600), label = "topShelf") { img ->
            if (img != null) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.5f)),
            ),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = blur }.background(Color.Black.copy(alpha = 0.22f)))

        // Glass dock under the first row; it fades out when you move into the grid.
        val dockAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "dock")
        val headerAlpha by animateFloatAsState(if (row == 0) 1f else 0f, tween(300), label = "header")
        if (headerAlpha > 0f) {
            HomeHeader(repo.userName, Modifier.padding(start = Metrics.Margin, top = 30.dp).graphicsLayer { alpha = headerAlpha })
            if (state.mode != Mode.Control) {
                TopBar(
                    backdrop, top, if (state.mode == Mode.Top) state.topIndex else -1,
                    Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = Metrics.Margin).graphicsLayer { alpha = headerAlpha },
                    onPick = { state.topIndex = it; state.mode = Mode.Top; select() },
                )
            }
        }
        if (dockAlpha > 0f) {
            Glass(
                backdrop,
                Modifier
                    .offset { IntOffset((Metrics.Margin - DockInset).roundToPx(), (Metrics.TopRowY - DockTop - scroll.value.dp).roundToPx()) }
                    .size(960.dp - (Metrics.Margin - DockInset) * 2, DockHeight)
                    .graphicsLayer { alpha = dockAlpha },
                shape = RoundedCornerShape(36.dp),
                tint = Color.White.copy(alpha = 0.06f),
                source = ambience,
            )
        }

        // Grid: only rows near the selection are composed.
        val visibleRows = (row - 2)..(row + 3)
        apps.forEachIndexed { i, entry ->
            if (i / Metrics.COLS in visibleRows) key(entry.pkg) {
                // When an app is moved or hidden, the others slide to their new place.
                val slide = spring<Float>(dampingRatio = 0.85f, stiffness = 320f)
                val x by animateFloatAsState(tileX(i).value, slide, label = "x")
                val baseY by animateFloatAsState((Metrics.TopRowY + Metrics.Pitch * (i / Metrics.COLS)).value, slide, label = "y")
                AppTile(
                    entry = entry,
                    visual = repo.visuals[entry.pkg],
                    focused = i == state.focus && state.mode != Mode.Control && state.mode != Mode.Top,
                    pressed = i == state.focus && state.pressed,
                    jiggling = state.mode == Mode.Jiggle && i == state.focus,
                    showLabel = i >= Metrics.COLS,
                    nudge = state.nudge,
                    modifier = Modifier
                        .offset { IntOffset(x.dp.roundToPx(), (baseY - scroll.value).dp.roundToPx()) }
                        .graphicsLayer {
                            // Rows scrolling off the top fade out, as on tvOS.
                            val y = baseY - scroll.value
                            alpha = ((y + 60f) / 100f).coerceIn(0f, 1f)
                        }
                        .tappable(onLong = { longTile(i) }) { tapTile(i) },
                )
            }
        }

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black))
        // Touch: tapping outside a menu or the Control Center closes it.
        if (state.mode == Mode.Menu) Box(Modifier.fillMaxSize().tappable { state.mode = Mode.Browse })
        if (state.mode == Mode.Control && !cc.typing) Box(Modifier.fillMaxSize().tappable { cc.close() })

        AnimatedVisibility(
            state.mode == Mode.Jiggle, Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            HintPill(backdrop, stringResource(R.string.hint_move, focused?.label.orEmpty()), Modifier)
        }

        AnimatedVisibility(
            state.mode == Mode.Control, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 24.dp),
            enter = fadeIn(tween(220)) + slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 420f)) { it / 2 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 2 },
        ) {
            cc.Panel(backdrop, wallpaperName, Modifier)
        }

        if (focused != null) {
            val tx = tileX(state.focus).value
            val ty = tileY(state.focus)
            val above = ty - MenuHeight.value - 14f > 16f
            val mx = (tx + Metrics.TileW.value / 2 - MenuWidth.value / 2)
                .coerceIn(Metrics.Margin.value, 960f - Metrics.Margin.value - MenuWidth.value)
            val my = if (above) ty - MenuHeight.value - 14f else ty + Metrics.TileH.value + 40f
            AnimatedVisibility(
                state.mode == Mode.Menu, Modifier.offset(mx.dp, my.dp),
                enter = fadeIn(tween(180)) + scaleIn(
                    spring(dampingRatio = 0.75f, stiffness = 600f), 0.9f, TransformOrigin(0.5f, if (above) 1f else 0f),
                ),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f),
            ) {
                ContextMenu(backdrop, focused.label, menu, state.menuIndex, Modifier, onPick = { state.menuIndex = it; select() })
            }
        }
        cc.PasswordLayer(backdrop) { focusRequester.requestFocus() }
    }

}
