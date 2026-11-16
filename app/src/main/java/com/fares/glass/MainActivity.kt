package com.fares.glass

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import com.fares.glass.data.AppEntry
import com.fares.glass.data.AppRepository
import com.fares.glass.gfx.Blur
import com.fares.glass.gfx.Wallpapers
import com.fares.glass.ui.Backdrop
import com.fares.glass.ui.Command
import com.fares.glass.ui.HomeScreen
import com.fares.glass.ui.HomeState
import com.fares.glass.ui.Typo
import com.fares.glass.ui.ControlCenter
import com.fares.glass.ui.Mode
import com.fares.glass.sys.BluetoothController
import com.fares.glass.sys.SystemInfo
import com.fares.glass.sys.SettingsShortcuts
import androidx.activity.result.contract.ActivityResultContracts
import com.fares.glass.sys.WifiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private val home = HomeState()
    private val backdrop = Backdrop()
    private val wallpaper = mutableStateOf<ImageBitmap?>(null)
    private val wallpaperName = mutableStateOf("")
    private lateinit var cc: ControlCenter
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cc.onPermissionsResult()
    }

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let(repo::invalidate)
            lifecycleScope.launch { repo.refresh() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(this)
        Typo.load(this)
        intent.getStringExtra("name")?.let(repo::setName)
        cc = ControlCenter(
            this, repo, WifiController(this), BluetoothController(this), SystemInfo(this), SettingsShortcuts(this),
            onOpen = ::startSafely,
            onRequestPermissions = { permissions.launch(it) },
            onNextWallpaper = { run(Command.NextWallpaper) },
            onClose = { home.mode = Mode.Browse },
        )
        // A home screen never closes on Back.
        onBackPressedDispatcher.addCallback(this) {}
        registerReceiver(packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        setContent {
            // The layout is designed for a 960 dp wide screen (a 1080p Fire TV). TVs report all kinds of
            // densities, so scale it to fill the screen whatever the resolution and dpi.
            val width = resources.displayMetrics.widthPixels
            // The layout has no room for enlarged text (TV font size settings): never scale it up.
            val font = LocalDensity.current.fontScale.coerceAtMost(1f)
            CompositionLocalProvider(LocalDensity provides Density(width / 960f, font)) {
                HomeScreen(
                    repo, home, backdrop, wallpaper.value, wallpaperName.value, cc,
                    onLaunch = ::launch, onCommand = ::run,
                )
            }
        }
        loadWallpaper()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { repo.refresh() }
    }

    override fun onPause() {
        super.onPause()
        if (home.mode == Mode.Control) cc.close()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("name")?.let(repo::setName)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) home.goHome()
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        super.onDestroy()
    }

    private fun loadWallpaper() {
        val index = repo.wallpaperIndex % Wallpapers.count(this)
        lifecycleScope.launch {
            val (sharp, frosted) = withContext(Dispatchers.Default) {
                val src = Wallpapers.load(this@MainActivity, index)
                // ~480 px wide with sigma 10: like a 40 px blur at 1080p, and very cheap.
                src to Blur.frosted(src, 480, 10f, 1.45f, 1.0f)
            }
            wallpaper.value = sharp.asImageBitmap()
            backdrop.frosted = frosted.asImageBitmap()
            wallpaperName.value = Wallpapers.name(this@MainActivity, index)
        }
    }

    private fun launch(entry: AppEntry, from: Rect) {
        val options = ActivityOptions.makeScaleUpAnimation(window.decorView, from.left, from.top, from.width(), from.height())
        try {
            startActivity(entry.launchIntent(), options.toBundle())
        } catch (e: Exception) {
            Log.w("Glass", "Can't launch ${entry.pkg}", e)
            packageManager.getLaunchIntentForPackage(entry.pkg)?.let { startSafely(it) }
        }
    }

    private fun run(command: Command) {
        when (command) {
            Command.Settings -> startSafely(Intent(Settings.ACTION_SETTINGS))
            Command.Network -> startSafely(Intent(Settings.ACTION_WIFI_SETTINGS))
            Command.Bluetooth -> startSafely(Intent("android.settings.CONTROLLERS_SETTINGS"))
            Command.Apps -> startSafely(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            is Command.AppInfo -> startSafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", command.pkg, null)),
            )
            Command.NextWallpaper -> {
                repo.setWallpaper((repo.wallpaperIndex + 1) % Wallpapers.count(this))
                loadWallpaper()
            }
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("Glass", "No activity for $intent", e)
            if (intent.action != Settings.ACTION_SETTINGS) startSafely(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
