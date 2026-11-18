package com.manish.liquidlauncher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.manish.liquidlauncher.ui.components.LocalAnimClock
import com.manish.liquidlauncher.ui.components.LocalReducedEffects
import com.manish.liquidlauncher.ui.components.LocalTilt
import com.manish.liquidlauncher.ui.components.rememberAnimClock
import com.manish.liquidlauncher.ui.components.rememberPowerSaveMode
import com.manish.liquidlauncher.ui.components.rememberTilt
import com.manish.liquidlauncher.ui.screens.AppContextMenu
import com.manish.liquidlauncher.ui.screens.AppDrawerSheet
import com.manish.liquidlauncher.ui.screens.FolderOverlay
import com.manish.liquidlauncher.ui.screens.HomeScreen
import com.manish.liquidlauncher.ui.screens.ThemeEngineSheet
import com.manish.liquidlauncher.ui.theme.LiquidGlassTheme
import com.manish.liquidlauncher.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            LiquidGlassTheme {
                LiquidLauncherRoot(viewModel)
            }
        }
    }

    // singleTask: pressing Home while we're already running lands here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onHomePressed()
    }
}

@Composable
fun LiquidLauncherRoot(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val config = viewModel.themeConfig

    // Persistable permission keeps the wallpaper working after reboot
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            viewModel.setWallpaper(uri)
        }
    }
    val pickWallpaper: () -> Unit = { picker.launch(arrayOf("image/*")) }

    BackHandler(enabled = true) { viewModel.handleBack() }

    val drawerOpen = viewModel.isDrawerOpen
    val powerSave by rememberPowerSaveMode()
    // Freeze wallpaper animation behind the drawer and whenever the Activity is backgrounded.
    val clock = rememberAnimClock(running = config.liveAnimations && !drawerOpen)
    val tilt = rememberTilt(enabled = config.tiltEffects && !drawerOpen && !powerSave)

    // Keep the glass depth, but avoid an expensive 22dp full-screen blur on mid-range phones.
    val targetBlur = when {
        !drawerOpen || !config.blurDrawer -> 0.dp
        powerSave -> 7.dp
        else -> 13.dp
    }
    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(380),
        label = "homeBlur"
    )
    val homeScale by animateFloatAsState(
        targetValue = if (drawerOpen) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "homeScale"
    )

    CompositionLocalProvider(
        LocalAnimClock provides clock,
        LocalTilt provides tilt,
        LocalReducedEffects provides powerSave
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF030712))) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = homeScale
                        scaleY = homeScale
                    }
                    .then(if (blurRadius > 0.5.dp) Modifier.blur(blurRadius) else Modifier)
            ) {
                HomeScreen(viewModel = viewModel, onPickWallpaper = pickWallpaper)
            }
            AppDrawerSheet(viewModel = viewModel)
            FolderOverlay(viewModel = viewModel)
            ThemeEngineSheet(viewModel = viewModel, onPickWallpaper = pickWallpaper)
            AppContextMenu(viewModel = viewModel)
        }
    }
}
