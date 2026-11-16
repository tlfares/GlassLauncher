package com.fares.glass.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent as AndroidKey
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.R
import com.fares.glass.data.AppRepository
import com.fares.glass.sys.BluetoothController
import com.fares.glass.sys.BtKind
import com.fares.glass.sys.SettingsShortcuts
import com.fares.glass.sys.SettingsShortcuts.Section
import com.fares.glass.sys.SystemInfo
import com.fares.glass.sys.WifiController
import com.fares.glass.sys.WifiNet
import java.util.Locale

enum class CcPage { Main, Wifi, Bluetooth }

/** Narrow, tall sheet, like the tvOS Control Center. */
val PanelWidth = 300.dp
val PanelHeight = 516.dp

private val Ink = Color(0xFF111114)
private val White = Color.White
private val WifiBlue = Color(0xFF0A84FF)
private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)
private val Purple = Color(0xFFBF5AF2)

/** One row of a detail page (Wi-Fi, Bluetooth). */
private sealed interface Line {
    val focusable: Boolean get() = true
    /** Stable identity: focus follows the item even when the list changes (device found, scan…). */
    val key: String
    data class Toggle(val title: String, val on: Boolean) : Line { override val key get() = "toggle" }
    data class Header(val title: String) : Line { override val focusable get() = false; override val key get() = "header:$title" }
    data class Network(val net: WifiNet) : Line { override val key get() = "net:${net.ssid}" }
    data class Device(val address: String, val name: String, val subtitle: String, val icon: ImageVector, val bonded: Boolean, val connected: Boolean) : Line {
        override val key get() = "dev:$address"
    }
    data class Action(val title: String, val icon: ImageVector) : Line { override val key get() = "action" }
    data class Link(val title: String, val section: Section) : Line { override val key get() = "link" }
    data class Note(val text: String) : Line { override val focusable get() = false; override val key get() = "note:$text" }
}

private class Tile(val icon: ImageVector, val title: String, val status: String, val active: Boolean, val accent: Color)
private val Section.icon: ImageVector
    get() = when (this) {
        Section.Display -> Icons.Rounded.Tv
        Section.Devices -> Icons.Rounded.SettingsRemote
        Section.Apps -> Icons.Rounded.Apps
        Section.Accessibility -> Icons.Rounded.Accessibility
        Section.About -> Icons.Rounded.Info
        Section.Network -> Icons.Rounded.Wifi
        Section.All -> Icons.Rounded.Settings
    }

/**
 * tvOS-style Control Center: a grid of modules, a System module, round shortcuts, and Wi-Fi /
 * Bluetooth pages that slide inside the panel. All remote navigation for it lives here.
 */
class ControlCenter(
    private val context: Context,
    private val repo: AppRepository,
    val wifi: WifiController,
    val bt: BluetoothController,
    val sys: SystemInfo,
    private val settings: SettingsShortcuts,
    private val onOpen: (Intent) -> Unit,
    /** Asks the user for missing runtime permissions (location, and Bluetooth on recent Android). */
    private val onRequestPermissions: (Array<String>) -> Unit,
    private val onNextWallpaper: () -> Unit,
    private val onClose: () -> Unit,
) {
    var page by mutableStateOf(CcPage.Main)
    var index by mutableIntStateOf(0)
    /** Selected item on a detail page, identified by its key. */
    var rowKey by mutableStateOf("toggle")
    /** Network whose password is being typed (the on-screen keyboard is open). */
    var passwordFor by mutableStateOf<WifiNet?>(null)
    var password by mutableStateOf("")
    private var forgetArmed: String? = null
    /** Bumped to re-read memory, storage and animations. */
    var tick by mutableIntStateOf(0)

    val typing get() = passwordFor != null

    /** Round shortcuts: sections this TV really lets Glass open (up to 4), plus "All settings". */
    private val shortcuts: List<Section> by lazy {
        listOf(Section.Display, Section.Devices, Section.Apps, Section.Accessibility, Section.About)
            .filter(settings::available).take(4) + Section.All
    }

    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun open(section: Section) {
        val intent = settings.intent(section) ?: settings.intent(Section.All) ?: return
        onOpen(intent)
    }

    private fun askPermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.filter { sys.missing(it) }
        if (wanted.isNotEmpty()) onRequestPermissions(wanted.toTypedArray())
    }

    /** After the user answers the permission requests. */
    fun onPermissionsResult() {
        wifi.refresh()
        wifi.scan()
        bt.refresh()
    }

    fun open(target: CcPage) {
        page = target
        index = when (target) { CcPage.Wifi -> 0; CcPage.Bluetooth -> 1; CcPage.Main -> 0 }
        rowKey = "toggle"
        wifi.start()
        bt.start()
        tick++
    }

    fun close() {
        wifi.stop()
        bt.stop()
        passwordFor = null
        onClose()
    }

    // ---- Main page ----

    private fun tiles(wallpaperName: String): List<Tile> {
        @Suppress("UNUSED_EXPRESSION") tick
        val anim = sys.animationScale()
        return listOf(
            Tile(if (wifi.enabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, "Wi-Fi",
                if (!wifi.enabled) str(R.string.cc_off) else wifi.current ?: str(R.string.cc_not_connected), wifi.enabled && wifi.current != null, WifiBlue),
            Tile(if (bt.enabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, "Bluetooth",
                when {
                    !bt.available -> str(R.string.cc_unavailable)
                    !bt.enabled -> str(R.string.cc_off)
                    else -> bt.paired.count { it.connected }.let {
                        if (it == 0) str(R.string.cc_no_devices) else context.resources.getQuantityString(R.plurals.cc_connected_count, it, it)
                    }
                }, bt.enabled, WifiBlue),
            Tile(Icons.Rounded.Animation, str(R.string.cc_animations),
                if (!sys.canWriteSettings()) str(R.string.cc_anim_adb) else when (anim) {
                    1f -> str(R.string.cc_anim_normal)
                    0f -> str(R.string.cc_anim_off)
                    else -> str(R.string.cc_anim_fast, anim)
                },
                anim < 1f, Orange),
            Tile(Icons.Rounded.Wallpaper, str(R.string.cc_wallpaper), wallpaperName, false, Purple),
            Tile(Icons.Rounded.ViewCarousel, "Top Shelf", str(if (repo.topShelf) R.string.cc_top_shelf_app else R.string.cc_wallpaper), repo.topShelf, Purple),
            Tile(Icons.Rounded.Visibility, str(R.string.cc_hidden_apps),
                if (repo.hiddenCount == 0) str(R.string.cc_hidden_none) else str(R.string.cc_hidden_count, repo.hiddenCount),
                repo.hiddenCount > 0, Green),
        )
    }

    private fun selectMain() {
        when (index) {
            0 -> { page = CcPage.Wifi; rowKey = "toggle"; askPermissions(); wifi.scan() }
            1 -> { page = CcPage.Bluetooth; rowKey = "toggle"; askPermissions() }
            2 -> { sys.cycleAnimations(); tick++ }
            3 -> onNextWallpaper()
            4 -> repo.toggleTopShelf()
            5 -> repo.unhideAll()
            6 -> open(if (settings.available(Section.About)) Section.About else Section.All)
            else -> open(shortcuts[index - 7])
        }
    }

    private fun moveMain(dx: Int, dy: Int) {
        val i = index
        index = when {
            i < 6 -> when {
                dx < 0 && i % 2 == 1 -> i - 1
                dx > 0 && i % 2 == 0 -> i + 1
                dy > 0 -> if (i < 4) i + 2 else 6
                dy < 0 && i >= 2 -> i - 2
                else -> i
            }
            i == 6 -> when {
                dy < 0 -> 4
                dy > 0 -> 7
                else -> i
            }
            else -> when {
                dx < 0 && i > 7 -> i - 1
                dx > 0 && i < 7 + shortcuts.lastIndex -> i + 1
                dy < 0 -> 6
                else -> i
            }
        }
    }

    // ---- Detail pages ----

    private fun rows(): List<Line> = when (page) {
        CcPage.Main -> emptyList()
        CcPage.Wifi -> buildList {
            add(Line.Toggle("Wi-Fi", wifi.enabled))
            if (wifi.enabled) {
                add(Line.Header(str(R.string.wifi_networks)))
                wifi.networks.forEach { add(Line.Network(it)) }
                if (wifi.networks.isEmpty()) {
                    add(Line.Note(str(if (wifi.searching) R.string.wifi_searching else R.string.wifi_none_found)))
                }
                add(Line.Action(str(R.string.wifi_rescan), Icons.Rounded.Search))
            }
            wifi.status?.let { add(Line.Note(it)) }
            if (settings.available(Section.Network)) add(Line.Link(str(R.string.wifi_system_settings), Section.Network))
        }
        CcPage.Bluetooth -> buildList {
            add(Line.Toggle("Bluetooth", bt.enabled))
            if (bt.enabled) {
                add(Line.Header(str(R.string.bt_my_devices)))
                bt.paired.forEach {
                    add(Line.Device(it.address, it.name, str(if (it.connected) R.string.bt_connected_kind else R.string.bt_not_connected_kind, str(it.kind.label)),
                        iconFor(it.kind), true, it.connected))
                }
                if (bt.paired.isEmpty()) add(Line.Note(str(R.string.bt_none_paired)))
                add(Line.Header(str(R.string.bt_other_devices)))
                bt.found.forEach { add(Line.Device(it.address, it.name, str(R.string.bt_ok_to_pair, str(it.kind.label)), iconFor(it.kind), false, false)) }
                add(Line.Action(str(if (bt.discovering) R.string.bt_searching else R.string.bt_search), Icons.Rounded.Search))
            }
            bt.status?.let { add(Line.Note(it)) }
            if (settings.available(Section.Devices)) add(Line.Link(str(R.string.bt_system_settings), Section.Devices))
        }
    }

    private fun iconFor(kind: BtKind) = when (kind) {
        BtKind.Audio -> Icons.Rounded.Headphones
        BtKind.Remote -> Icons.Rounded.SettingsRemote
        BtKind.Gamepad -> Icons.Rounded.SportsEsports
        BtKind.Phone -> Icons.Rounded.Smartphone
        BtKind.Computer -> Icons.Rounded.Computer
        BtKind.Other -> Icons.Rounded.Devices
    }

    private fun focusableRows(list: List<Line>) = list.indices.filter { list[it].focusable }

    /** Index of the selected item; if it has left the list, the first selectable one. */
    private fun rowIndex(list: List<Line>): Int =
        list.indexOfFirst { it.key == rowKey }.takeIf { it >= 0 } ?: focusableRows(list).firstOrNull() ?: 0

    private fun moveDetail(dy: Int) {
        val list = rows()
        val stops = focusableRows(list)
        if (stops.isEmpty()) return
        val pos = stops.indexOf(rowIndex(list)).coerceAtLeast(0)
        rowKey = list[stops[(pos + dy).coerceIn(0, stops.lastIndex)]].key
    }

    private fun selectDetail() {
        val list = rows()
        val r = list.getOrNull(rowIndex(list)) ?: return
        when (r) {
            is Line.Toggle -> if (page == CcPage.Wifi) {
                // Android 10+ doesn't let apps turn Wi-Fi on or off: open the system screen.
                if (!wifi.turn(!r.on)) open(Section.Network)
            } else {
                // Android 13+: turn on through the standard system dialog, turn off in Settings.
                if (!bt.turn(!r.on)) {
                    if (!r.on) onOpen(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else open(Section.Devices)
                }
            }
            is Line.Network -> when {
                r.net.connected -> Unit
                !wifi.canConnect -> open(Section.Network) // Android 10+: connect from the system screen
                wifi.needsPassword(r.net) -> { password = ""; passwordFor = r.net }
                else -> wifi.connect(r.net)
            }
            is Line.Device -> when {
                !r.bonded -> bt.pair(r.address)
                bt.paired.firstOrNull { it.address == r.address }?.protected == true ->
                    bt.status = str(R.string.bt_protected)
                forgetArmed == r.address -> { forgetArmed = null; bt.forget(r.address) }
                else -> { forgetArmed = r.address; bt.status = str(R.string.bt_forget_confirm, r.name) }
            }
            is Line.Action -> if (page == CcPage.Wifi) wifi.scan() else bt.discover()
            is Line.Link -> open(r.section)
            else -> Unit
        }
    }

    fun submitPassword() {
        val net = passwordFor ?: return
        passwordFor = null
        if (password.isNotEmpty()) wifi.connect(net, password)
        password = ""
    }

    fun cancelPassword() {
        passwordFor = null
        password = ""
    }

    /** Keys while the Control Center is open. Returns true if the key is consumed. */
    fun onKey(e: AndroidKey): Boolean {
        if (typing) {
            if (e.keyCode == AndroidKey.KEYCODE_BACK) {
                if (e.action == AndroidKey.ACTION_UP) cancelPassword()
                return true
            }
            return false // the text field and the on-screen keyboard handle the rest
        }
        if (e.action != AndroidKey.ACTION_DOWN) return true
        val (dx, dy) = when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_LEFT -> -1 to 0
            AndroidKey.KEYCODE_DPAD_RIGHT -> 1 to 0
            AndroidKey.KEYCODE_DPAD_UP -> 0 to -1
            AndroidKey.KEYCODE_DPAD_DOWN -> 0 to 1
            else -> 0 to 0
        }
        when (e.keyCode) {
            AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER ->
                if (e.repeatCount == 0) { if (page == CcPage.Main) selectMain() else selectDetail() }
            AndroidKey.KEYCODE_BACK -> if (e.repeatCount == 0) {
                if (page == CcPage.Main) close() else { page = CcPage.Main; forgetArmed = null }
            }
            AndroidKey.KEYCODE_MENU -> if (e.repeatCount == 0) close()
            else -> if (dx != 0 || dy != 0) {
                if (page == CcPage.Main) moveMain(dx, dy)
                else if (dx < 0) { page = CcPage.Main; forgetArmed = null } else if (dy != 0) moveDetail(dy)
            }
        }
        return true
    }

    // ---- Rendering ----

    @Composable
    fun Panel(backdrop: Backdrop, wallpaperName: String, modifier: Modifier) {
        Glass(backdrop, modifier.size(PanelWidth, PanelHeight), RoundedCornerShape(36.dp), tint = White.copy(alpha = 0.08f)) {
            AnimatedContent(
                page,
                transitionSpec = {
                    val forward = targetState != CcPage.Main
                    (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(220)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
                },
                label = "ccPage",
            ) { p ->
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    if (p == CcPage.Main) MainPage(wallpaperName) else DetailPage(p)
                }
            }
        }
    }

    @Composable
    private fun MainPage(wallpaperName: String) {
        val (time, date) = rememberClock(rememberNow())
        val tiles = tiles(wallpaperName)
        Column(Modifier.fillMaxSize()) {
            BasicText(time, style = txt(White, 34.sp, FontWeight.Bold))
            BasicText(date, style = txt(White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium))
            Spacer(Modifier.height(12.dp))
            for (r in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 2) {
                        val i = r * 2 + c
                        ModuleTile(tiles[i], index == i, Modifier.weight(1f).tappable { index = i; selectMain() })
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            SystemModule(index == 6, Modifier.tappable { index = 6; selectMain() })
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                shortcuts.forEachIndexed { k, s -> RoundButton(s.icon, index == 7 + k, Modifier.tappable { index = 7 + k; selectMain() }) }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                if (index >= 7) str(shortcuts[index - 7].title) else " ",
                Modifier.fillMaxWidth(),
                style = txt(White.copy(alpha = 0.85f), 13.sp, FontWeight.SemiBold).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
    }

    @Composable
    private fun focusAmount(focused: Boolean) =
        animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 650f), label = "focus").value

    @Composable
    private fun ModuleTile(t: Tile, focused: Boolean, modifier: Modifier) {
        val f = focusAmount(focused)
        val fg = lerp(White, Ink, f)
        Column(
            modifier
                .height(76.dp)
                .graphicsLayer { scaleX = 1f + 0.06f * f; scaleY = 1f + 0.06f * f }
                .background(lerp(White.copy(alpha = 0.10f), White.copy(alpha = 0.95f), f), RoundedCornerShape(24.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(
                    if (t.active) t.accent else lerp(White.copy(alpha = 0.16f), Ink.copy(alpha = 0.08f), f),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Image(rememberVectorPainter(t.icon), null, Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(if (t.active) White else fg))
            }
            Column {
                BasicText(t.title, style = txt(fg, 13.sp, FontWeight.SemiBold), maxLines = 1)
                BasicText(t.status, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun SystemModule(focused: Boolean, modifier: Modifier) {
        val f = focusAmount(focused)
        val fg = lerp(White, Ink, f)
        val mem = remember(tick) { sys.memory() }
        val disk = remember(tick) { sys.storage() }
        Column(
            modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = 1f + 0.03f * f; scaleY = 1f + 0.03f * f }
                .background(lerp(White.copy(alpha = 0.10f), White.copy(alpha = 0.95f), f), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(str(R.string.cc_system), style = txt(fg, 13.sp, FontWeight.SemiBold))
                Spacer(Modifier.weight(1f))
                BasicText(wifi.ip ?: "", style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            // Both bars start where the longer label ends, so they line up in every language.
            val labels = listOf(str(R.string.cc_memory), str(R.string.cc_storage))
            val measurer = rememberTextMeasurer()
            val labelStyle = txt(fg.copy(alpha = 0.75f), 11.sp, FontWeight.Medium)
            val labelWidth = with(LocalDensity.current) {
                labels.maxOf { measurer.measure(it, labelStyle).size.width }.toDp() + 8.dp
            }
            UsageBar(labels[0], labelWidth, mem, fg, f)
            Spacer(Modifier.height(4.dp))
            UsageBar(labels[1], labelWidth, disk, fg, f)
        }
    }

    @Composable
    private fun UsageBar(label: String, labelWidth: Dp, u: SystemInfo.Usage, fg: Color, f: Float) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.width(labelWidth), style = txt(fg.copy(alpha = 0.75f), 11.sp, FontWeight.Medium), maxLines = 1)
            Canvas(Modifier.weight(1f).height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(lerp(White.copy(alpha = 0.18f), Ink.copy(alpha = 0.12f), f), cornerRadius = r)
                drawRoundRect(if (u.usedFraction > 0.85f) Orange else lerp(White, Ink, f),
                    size = Size(size.width * u.usedFraction.coerceIn(0.02f, 1f), size.height), cornerRadius = r)
            }
            Spacer(Modifier.width(8.dp))
            BasicText(u.free, Modifier.width(74.dp), style = txt(fg.copy(alpha = 0.6f), 10.sp, FontWeight.Medium), maxLines = 1)
        }
    }

    @Composable
    private fun RoundButton(icon: ImageVector, focused: Boolean, modifier: Modifier) {
        val f = focusAmount(focused)
        Box(
            modifier
                .size(44.dp)
                .graphicsLayer { scaleX = 1f + 0.1f * f; scaleY = 1f + 0.1f * f }
                .clip(CircleShape)
                .background(lerp(White.copy(alpha = 0.12f), White.copy(alpha = 0.95f), f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(rememberVectorPainter(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(lerp(White, Ink, f)))
        }
    }

    @Composable
    private fun DetailPage(p: CcPage) {
        val list = rows()
        Column {
            Row(Modifier.tappable { page = CcPage.Main; forgetArmed = null }, verticalAlignment = Alignment.CenterVertically) {
                Image(rememberVectorPainter(Icons.Rounded.ChevronLeft), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(White.copy(alpha = 0.7f)))
                BasicText(if (p == CcPage.Wifi) "Wi-Fi" else "Bluetooth", style = txt(White, 24.sp, FontWeight.Bold))
            }
            Spacer(Modifier.height(12.dp))
            // Sliding window: the selected row stays visible.
            val window = 10
            val current = rowIndex(list)
            val start = (current - 3).coerceIn(0, (list.size - window).coerceAtLeast(0))
            list.drop(start).take(window).forEachIndexed { k, r -> DetailRow(r, start + k == current) }
        }
    }

    @Composable
    private fun DetailRow(r: Line, focused: Boolean) {
        when (r) {
            is Line.Header -> BasicText(r.title.uppercase(Locale.getDefault()), Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                style = txt(White.copy(alpha = 0.55f), 11.sp, FontWeight.SemiBold))
            is Line.Note -> BasicText(r.text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = txt(White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium), maxLines = 2)
            else -> {
                val f = focusAmount(focused)
                val fg = lerp(White, Ink, f)
                Row(
                    Modifier
                        .tappable { rowKey = r.key; selectDetail() }
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .graphicsLayer { scaleX = 1f + 0.025f * f; scaleY = 1f + 0.025f * f }
                        .background(lerp(White.copy(alpha = 0.08f), White.copy(alpha = 0.95f), f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (r) {
                        is Line.Toggle -> {
                            BasicText(r.title, Modifier.weight(1f), style = txt(fg, 14.sp, FontWeight.SemiBold))
                            Switch(r.on)
                        }
                        is Line.Network -> {
                            SignalBars(r.net.level, fg)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(r.net.ssid, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = when {
                                    r.net.connected -> wifi.ip?.let { str(R.string.wifi_connected_ip, it) } ?: str(R.string.wifi_connected)
                                    r.net.saved -> str(R.string.wifi_saved)
                                    else -> null
                                }
                                sub?.let { BasicText(it, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium)) }
                            }
                            if (r.net.secure) Icon(Icons.Rounded.Lock, fg.copy(alpha = 0.6f), 15)
                            if (r.net.connected) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Check, if (f > 0.5f) Ink else WifiBlue, 18) }
                        }
                        is Line.Device -> {
                            Icon(r.icon, if (r.connected && f < 0.5f) WifiBlue else fg, 20)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(r.name, style = txt(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(r.subtitle, style = txt(fg.copy(alpha = 0.6f), 11.sp, FontWeight.Medium), maxLines = 1)
                            }
                        }
                        is Line.Action -> {
                            Icon(r.icon, fg, 18)
                            Spacer(Modifier.width(10.dp))
                            BasicText(r.title, style = txt(fg, 14.sp, FontWeight.SemiBold))
                        }
                        is Line.Link -> {
                            BasicText(r.title, Modifier.weight(1f), style = txt(fg, 13.sp, FontWeight.Medium), maxLines = 1)
                            Icon(Icons.Rounded.OpenInNew, fg.copy(alpha = 0.6f), 16)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    @Composable
    private fun Icon(icon: ImageVector, tint: Color, size: Int) =
        Image(rememberVectorPainter(icon), null, Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint))

    @Composable
    private fun Switch(on: Boolean) {
        val k by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "switch")
        Canvas(Modifier.size(42.dp, 25.dp)) {
            drawRoundRect(lerp(Color(0xFF787880).copy(alpha = 0.45f), Green, k), cornerRadius = CornerRadius(size.height / 2))
            val d = size.height - 4.dp.toPx()
            drawCircle(White, d / 2, Offset(2.dp.toPx() + d / 2 + (size.width - d - 4.dp.toPx()) * k, size.height / 2))
        }
    }

    @Composable
    private fun SignalBars(level: Int, color: Color) {
        Canvas(Modifier.size(18.dp, 14.dp)) {
            val w = size.width / 4.6f
            for (b in 0 until 4) {
                val h = size.height * (b + 1) / 4f
                drawRoundRect(
                    if (b < level.coerceAtLeast(1)) color else color.copy(alpha = 0.25f),
                    topLeft = Offset(b * w * 1.2f, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 3),
                )
            }
        }
    }

    /** Wi-Fi password entry, centered on screen (place it at the root). */
    @Composable
    fun PasswordLayer(backdrop: Backdrop, onTypingDone: () -> Unit) {
        val net = passwordFor ?: return
        val field = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(net) {
            field.requestFocus()
            keyboard?.show()
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Glass(backdrop, Modifier.width(440.dp), RoundedCornerShape(32.dp), tint = White.copy(alpha = 0.10f)) {
                Column(Modifier.padding(24.dp)) {
                    BasicText(str(R.string.wifi_password), style = txt(White, 22.sp, FontWeight.Bold))
                    BasicText(str(R.string.wifi_password_for, net.ssid), style = txt(White.copy(alpha = 0.7f), 14.sp, FontWeight.Medium))
                    Spacer(Modifier.height(16.dp))
                    BasicTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        textStyle = txt(Ink, 18.sp, FontWeight.Medium),
                        cursorBrush = SolidColor(WifiBlue),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            submitPassword()
                            onTypingDone()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(field)
                            .background(White.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicText(str(R.string.wifi_password_hint),
                        style = txt(White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium))
                }
            }
        }
    }
}
