package com.manish.liquidlauncher.viewmodel

import android.app.ActivityOptions
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manish.liquidlauncher.data.LauncherPrefs
import com.manish.liquidlauncher.model.AppInfo
import com.manish.liquidlauncher.model.FolderState
import com.manish.liquidlauncher.model.LiquidThemeConfig
import com.manish.liquidlauncher.util.drawableToBitmap
import com.manish.liquidlauncher.util.extractDominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val prefs = LauncherPrefs(application)

    // ---- App lists -------------------------------------------------------------------------
    var allApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var installedApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var dockApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var frequentApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var hiddenPackages by mutableStateOf(prefs.loadHidden())
        private set

    // ---- UI state --------------------------------------------------------------------------
    var searchQuery by mutableStateOf("")
    var isDrawerOpen by mutableStateOf(false)
        private set
    var isThemeEngineOpen by mutableStateOf(false)
    var openedFolder by mutableStateOf<FolderState?>(null)
    var menuApp by mutableStateOf<AppInfo?>(null)
    var homeTick by mutableIntStateOf(0)
        private set
    var customWallpaperUri by mutableStateOf<Uri?>(prefs.wallpaperUri?.let { Uri.parse(it) })
        private set
    var themeConfig by mutableStateOf(prefs.loadTheme())
        private set
    var torchOn by mutableStateOf(false)
        private set

    // ---- Internals -------------------------------------------------------------------------
    private var storedDock: List<String>? = prefs.loadDock()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var torchId: String? = null
    private val cameraManager: CameraManager? =
        app.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadInstalledApps(delayMs = 500)
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) torchOn = enabled
        }
    }

    init {
        // Live-refresh when apps are installed / removed / updated
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // Flashlight tile
        try {
            val cm = cameraManager
            if (cm != null) {
                torchId = cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (torchId != null) {
                    cm.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
                }
            }
        } catch (_: Exception) {
            torchId = null
        }

        loadInstalledApps()
    }

    override fun onCleared() {
        try {
            app.unregisterReceiver(packageReceiver)
        } catch (_: Exception) {
        }
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
        super.onCleared()
    }

    // ---- Loading ---------------------------------------------------------------------------
    fun loadInstalledApps(delayMs: Long = 0L) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val apps = withContext(Dispatchers.Default) { queryApps() }
            allApps = apps
            rebuild()
        }
    }

    private fun queryApps(): List<AppInfo> {
        val pm = app.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != app.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val bitmap = drawableToBitmap(ri.loadIcon(pm))
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    activityName = ri.activityInfo.name,
                    icon = bitmap.asImageBitmap(),
                    tint = extractDominantColor(bitmap)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun defaultDock(visible: List<AppInfo>): List<String> {
        val pm = app.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        )
        val known = visible.map { it.packageName }.toSet()
        val picks = mutableListOf<String>()
        for (probe in probes) {
            val pkg = try {
                pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (_: Exception) {
                null
            }
            if (pkg != null && pkg in known && pkg !in picks) picks.add(pkg)
        }
        for (a in visible) {
            if (picks.size >= 4) break
            if (a.packageName !in picks) picks.add(a.packageName)
        }
        return picks
    }

    private fun rebuild() {
        val visible = allApps.filter { it.packageName !in hiddenPackages }
        installedApps = visible
        val byPkg = visible.associateBy { it.packageName }
        val dockPackages = storedDock ?: defaultDock(visible)
        dockApps = dockPackages.mapNotNull { byPkg[it] }.take(MAX_DOCK)
        val counts = prefs.launchCounts()
        frequentApps = visible
            .filter { (counts[it.packageName] ?: 0) > 0 }
            .sortedByDescending { counts[it.packageName] ?: 0 }
            .take(4)
    }

    // ---- Navigation / overlays -------------------------------------------------------------
    fun openDrawer() {
        menuApp = null
        openedFolder = null
        isDrawerOpen = true
    }

    fun closeDrawer() {
        isDrawerOpen = false
        searchQuery = ""
    }

    fun onHomePressed() {
        isDrawerOpen = false
        isThemeEngineOpen = false
        openedFolder = null
        menuApp = null
        searchQuery = ""
        homeTick++
    }

    fun handleBack() {
        when {
            menuApp != null -> menuApp = null
            isThemeEngineOpen -> isThemeEngineOpen = false
            openedFolder != null -> openedFolder = null
            isDrawerOpen -> {
                if (searchQuery.isNotEmpty()) searchQuery = "" else closeDrawer()
            }
        }
    }

    // ---- Actions ---------------------------------------------------------------------------
    fun launchApp(context: Context, view: View?, app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try {
            val options = if (view != null) {
                ActivityOptions.makeScaleUpAnimation(view, view.width / 2, view.height / 2, 1, 1)
            } else {
                ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out)
            }
            context.startActivity(intent, options.toBundle())
            prefs.bumpLaunch(app.packageName)
            rebuild()
            closeDrawer()
            openedFolder = null
            menuApp = null
        } catch (_: Exception) {
            Toast.makeText(context, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
            loadInstalledApps()
        }
    }

    fun openAppInfo(context: Context, app: AppInfo) {
        safeStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        menuApp = null
    }

    fun uninstall(context: Context, app: AppInfo) {
        safeStart(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        menuApp = null
    }

    fun openSettingsScreen(context: Context, action: String) {
        safeStart(context, Intent(action))
    }

    fun webSearch(context: Context, query: String) {
        if (query.isBlank()) return
        safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
        closeDrawer()
    }

    fun openDefaultLauncherPicker(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            safeStart(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    /** Pull down the notification shade (needs the normal EXPAND_STATUS_BAR permission). */
    fun expandNotifications(context: Context) {
        try {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(service)
        } catch (_: Throwable) {
        }
    }

    fun toggleTorch() {
        val id = torchId ?: run {
            Toast.makeText(app, "No flashlight available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            cameraManager?.setTorchMode(id, !torchOn)
        } catch (_: Exception) {
        }
    }

    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Action not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Dock / hidden ---------------------------------------------------------------------
    fun isInDock(packageName: String): Boolean = dockApps.any { it.packageName == packageName }

    fun toggleDock(target: AppInfo) {
        val current = dockApps.map { it.packageName }
        if (target.packageName in current) {
            storedDock = current - target.packageName
        } else {
            if (current.size >= MAX_DOCK) {
                Toast.makeText(app, "Dock is full - remove an app first", Toast.LENGTH_SHORT).show()
                return
            }
            storedDock = current + target.packageName
        }
        prefs.saveDock(storedDock ?: emptyList())
        rebuild()
        menuApp = null
    }

    fun hideApp(target: AppInfo) {
        hiddenPackages = hiddenPackages + target.packageName
        prefs.saveHidden(hiddenPackages)
        rebuild()
        menuApp = null
    }

    fun restoreHiddenApps() {
        hiddenPackages = emptySet()
        prefs.saveHidden(hiddenPackages)
        rebuild()
    }

    // ---- Theme / wallpaper -----------------------------------------------------------------
    fun updateTheme(newConfig: LiquidThemeConfig) {
        themeConfig = newConfig
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(250)
            prefs.saveTheme(newConfig)
        }
    }

    fun setWallpaper(uri: Uri?) {
        customWallpaperUri = uri
        prefs.wallpaperUri = uri?.toString()
    }

    companion object {
        const val MAX_DOCK = 5
    }
}
