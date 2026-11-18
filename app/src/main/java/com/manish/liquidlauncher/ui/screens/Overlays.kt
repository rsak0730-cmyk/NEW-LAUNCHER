package com.manish.liquidlauncher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.liquidlauncher.model.AppInfo
import com.manish.liquidlauncher.model.FolderState
import com.manish.liquidlauncher.ui.components.GlassButton
import com.manish.liquidlauncher.ui.components.LiquidAppIconItem
import com.manish.liquidlauncher.ui.components.LiquidGlassSurface
import com.manish.liquidlauncher.ui.components.popIn
import com.manish.liquidlauncher.viewmodel.LauncherViewModel

/** Pop-up folder with a bouncy open/close. */
@Composable
fun FolderOverlay(viewModel: LauncherViewModel) {
    val config = viewModel.themeConfig
    val context = LocalContext.current
    val view = LocalView.current
    val folder = viewModel.openedFolder
    var last by remember { mutableStateOf<FolderState?>(null) }
    SideEffect { if (folder != null) last = folder }
    val shown = folder ?: last

    AnimatedVisibility(
        visible = folder != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF02060D).copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.openedFolder = null }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.popIn()) {
                LiquidGlassSurface(
                    config = config,
                    cornerRadius = 32.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = shown?.title ?: "",
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            items(shown?.apps ?: emptyList<AppInfo>(), key = { it.packageName }) { app ->
                                Box(contentAlignment = Alignment.TopCenter) {
                                    LiquidAppIconItem(
                                        app = app,
                                        config = config,
                                        entranceIndex = 0,
                                        onLongClick = { viewModel.menuApp = app },
                                        onClick = { viewModel.launchApp(context, view, app) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Long-press menu for any app: open, info, uninstall, dock, hide. */
@Composable
fun AppContextMenu(viewModel: LauncherViewModel) {
    val config = viewModel.themeConfig
    val context = LocalContext.current
    val view = LocalView.current
    val app = viewModel.menuApp
    var last by remember { mutableStateOf<AppInfo?>(null) }
    SideEffect { if (app != null) last = app }
    val shown = app ?: last

    AnimatedVisibility(
        visible = app != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(140))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.menuApp = null }
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.popIn()) {
                LiquidGlassSurface(
                    config = config,
                    cornerRadius = 30.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                ) {
                    if (shown != null) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                bitmap = shown.icon,
                                contentDescription = shown.label,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                            Text(
                                text = shown.label,
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            val inDock = viewModel.isInDock(shown.packageName)
                            GlassButton("Open", config, Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow) {
                                viewModel.launchApp(context, view, shown)
                            }
                            GlassButton("App info", config, Modifier.fillMaxWidth(), Icons.Rounded.Info) {
                                viewModel.openAppInfo(context, shown)
                            }
                            GlassButton(
                                if (inDock) "Remove from dock" else "Add to dock",
                                config, Modifier.fillMaxWidth(), Icons.Rounded.PushPin
                            ) { viewModel.toggleDock(shown) }
                            GlassButton("Hide app", config, Modifier.fillMaxWidth(), Icons.Rounded.VisibilityOff) {
                                viewModel.hideApp(shown)
                            }
                            GlassButton(
                                "Uninstall", config, Modifier.fillMaxWidth(), Icons.Rounded.Delete,
                                tint = Color(0xFFFF6B6B)
                            ) { viewModel.uninstall(context, shown) }
                        }
                    }
                }
            }
        }
    }
}
