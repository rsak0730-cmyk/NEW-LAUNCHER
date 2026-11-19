package com.manish.liquidlauncher.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.liquidlauncher.model.AppInfo
import com.manish.liquidlauncher.model.FolderState
import com.manish.liquidlauncher.model.LiquidThemeConfig
import com.manish.liquidlauncher.notifications.NotificationHub
import com.manish.liquidlauncher.ui.components.BatteryWidget
import com.manish.liquidlauncher.ui.components.ClockWidget
import com.manish.liquidlauncher.ui.components.GaugeCard
import com.manish.liquidlauncher.ui.components.GlassIconButton
import com.manish.liquidlauncher.ui.components.LiquidAppIconItem
import com.manish.liquidlauncher.ui.components.LiquidFolderItem
import com.manish.liquidlauncher.ui.components.LiquidGlassSurface
import com.manish.liquidlauncher.ui.components.LiquidWallpaper
import com.manish.liquidlauncher.ui.components.MusicWidget
import com.manish.liquidlauncher.ui.components.QuickTile
import com.manish.liquidlauncher.ui.components.SectionTitle
import com.manish.liquidlauncher.ui.components.SkyWidget
import com.manish.liquidlauncher.ui.components.accentColor
import com.manish.liquidlauncher.ui.components.jellyTouch
import com.manish.liquidlauncher.ui.components.rememberBatteryInfo
import com.manish.liquidlauncher.util.SoundFx
import com.manish.liquidlauncher.viewmodel.LauncherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface HomeCell {
    data class FolderCell(val folder: FolderState) : HomeCell
    data class AppCell(val app: AppInfo) : HomeCell
}

private val SocialKeys = listOf("whatsapp", "instagram", "telegram", "discord", "snapchat", "facebook", "twitter", "reddit", "signal")
private val GoogleKeys = listOf("google", "chrome", "youtube", "maps")
private const val GRID_COLUMNS = 4

private fun UsageStatFraction(used: Long, total: Long): Float =
    if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

private fun readMemory(context: Context): UsageStat {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return UsageStat(used = info.totalMem - info.availMem, total = info.totalMem)
}

private fun readStorage(): UsageStat {
    return try {
        val stat = android.os.StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        UsageStat(used = total - free, total = total)
    } catch (_: Exception) {
        UsageStat(0, 0)
    }
}

private fun gb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_073_741_824.0)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: LauncherViewModel, onPickWallpaper: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val config = viewModel.themeConfig
    val accent = config.accentColor()
    val battery by rememberBatteryInfo()
    var firstPage by remember { mutableStateOf(true) }

    var totalPages by remember { mutableIntStateOf(2) }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { totalPages })

    val onLaunch: (AppInfo) -> Unit = { viewModel.launchApp(context, view, it) }
    val onMenu: (AppInfo) -> Unit = { viewModel.menuApp = it }

    LaunchedEffect(pagerState.currentPage) {
        if (firstPage) firstPage = false else SoundFx.play(SoundFx.SWISH)
    }

    LaunchedEffect(viewModel.homeTick) {
        if (viewModel.homeTick > 0) pagerState.animateScrollToPage(1)
    }

    val customStates = remember(viewModel.customFolders, viewModel.installedApps) {
        val byPkg = viewModel.installedApps.associateBy { it.packageName }
        viewModel.customFolders.mapNotNull { cf ->
            val apps = cf.packages.mapNotNull { byPkg[it] }
            if (apps.isEmpty()) null else FolderState(cf.name, apps)
        }
    }
    val homeApps = remember(viewModel.installedApps, viewModel.dockApps, customStates) {
        val dock = viewModel.dockApps.map { it.packageName }.toSet()
        val custom = customStates.flatMap { f -> f.apps.map { it.packageName } }.toSet()
        viewModel.installedApps.filter { it.packageName !in dock && it.packageName !in custom }
    }
    val folders = remember(homeApps, customStates) {
        val social = homeApps.filter { a -> SocialKeys.any { a.packageName.contains(it) } }
        val socialSet = social.map { it.packageName }.toSet()
        val google = homeApps.filter { a ->
            a.packageName !in socialSet && GoogleKeys.any { a.packageName.contains(it) }
        }
        customStates + listOfNotNull(
            if (social.size >= 2) FolderState("Social", social) else null,
            if (google.size >= 2) FolderState("Google", google) else null
        )
    }
    val looseApps = remember(homeApps, folders) {
        val inFolders = folders.flatMap { f -> f.apps.map { it.packageName } }.toSet()
        homeApps.filter { it.packageName !in inFolders }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { viewModel.lockScreen(context) },
                    onLongPress = { viewModel.isThemeEngineOpen = true }
                )
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -90f) viewModel.openDrawer()
                        else if (total > 170f) viewModel.expandNotifications(context)
                        total = 0f
                    },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy }
                )
            }
    ) {
        LiquidWallpaper(
            config = config,
            imageUri = viewModel.customWallpaperUri,
            pageOffset = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassSurface(config = config, cornerRadius = 50.dp, modifier = Modifier.weight(1f).height(46.dp).jellyTouch(haptics = config.haptics, pressX = 0.97f, pressY = 0.92f) { viewModel.openSearch(context) }) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search", tint = accent, modifier = Modifier.width(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Search ${viewModel.installedApps.size} apps...", color = Color.White.copy(alpha = 0.78f), fontFamily = FontFamily.SansSerif, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                GlassIconButton(Icons.Rounded.SwapHoriz, "Switch Default Launcher", config) { viewModel.openDefaultLauncherPicker(context) }
                GlassIconButton(Icons.Rounded.Wallpaper, "Import Wallpaper", config, onClick = onPickWallpaper)
                GlassIconButton(Icons.Rounded.Tune, "Theme Engine", config, tint = accent) { viewModel.isThemeEngineOpen = true }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)) {
                val availableHeight = maxHeight
                val cellHeight = (60f * config.iconScale).dp + if (config.showLabels) 26.dp else 8.dp
                val rowGap = 14.dp
                val widgetBlock = 114.dp + 16.dp
                val rowsFull = rowsThatFit(availableHeight, cellHeight, rowGap)
                val rowsFirst = rowsThatFit(availableHeight - widgetBlock, cellHeight, rowGap)

                SideEffect {
                    val wanted = 2 + if (looseApps.size > rowsFull * GRID_COLUMNS) (looseApps.size + rowsFull * GRID_COLUMNS - 1) / (rowsFull * GRID_COLUMNS) else 0
                    if (totalPages != wanted) totalPages = wanted
                }

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    Box(modifier = Modifier.fillMaxSize().pageTransform(pagerState, page)) {
                        when (page) {
                            0 -> DashboardPage(viewModel, config, onLaunch, onMenu)
                            1 -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().height(114.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ClockWidget(config = config, modifier = Modifier.weight(1.45f).fillMaxHeight())
                                    BatteryWidget(config = config, battery = battery, modifier = Modifier.weight(0.85f).fillMaxHeight())
                                }
                                val cells: List<HomeCell> = folders.map<FolderState, HomeCell> { HomeCell.FolderCell(it) } + looseApps.map<AppInfo, HomeCell> { HomeCell.AppCell(it) }
                                val firstPageApps = cells.take(rowsFirst * GRID_COLUMNS)
                                val extraPages = looseApps.drop(firstPageApps.count { it is HomeCell.AppCell })
                                val pageIndex = page - 1
                                val pageCells = if (pageIndex == 0) firstPageApps else extraPages.chunked(rowsFull * GRID_COLUMNS).getOrElse(pageIndex - 1) { emptyList() }.map { HomeCell.AppCell(it) }
                                CellGrid(cells = pageCells, rows = if (pageIndex == 0) rowsFirst else rowsFull, config = config, animateIn = true, onFolder = { viewModel.openedFolder = it }, onLaunch = onLaunch, onMenu = onMenu)
                            }
                            else -> CellGrid(cells = looseApps.drop(rowsFirst * GRID_COLUMNS).drop((page - 2) * rowsFull * GRID_COLUMNS).take(rowsFull * GRID_COLUMNS).map { HomeCell.AppCell(it) }, rows = rowsFull, config = config, animateIn = true, onFolder = { viewModel.openedFolder = it }, onLaunch = onLaunch, onMenu = onMenu)
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(modifier = Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(totalPages) { index ->
                        val selected = pagerState.currentPage == index
                        val dotWidth by animateDpAsState(targetValue = if (selected) 20.dp else 6.dp, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "dotWidth")
                        Box(modifier = Modifier.height(6.dp).width(dotWidth).background(if (selected) accent else Color.White.copy(alpha = 0.35f), CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { scope.launch { pagerState.animateScrollToPage(index) } })
                    }
                }
                LiquidGlassSurface(config = config, cornerRadius = 32.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        viewModel.dockApps.forEach { app -> LiquidAppIconItem(app = app, config = config, showLabel = false, iconBoxSize = (56f * config.iconScale).dp, bob = false, entranceIndex = -1, onLongClick = { onMenu(app) }, onClick = { onLaunch(app) }) }
                    }
                }
            }
        }
    }
}

private data class UsageStat(val used: Long, val total: Long)

private fun rowsThatFit(available: Dp, cell: Dp, gap: Dp): Int =
    (((available + gap) / (cell + gap)).toInt()).coerceIn(1, 8)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pageTransform(state: PagerState, page: Int): Modifier = graphicsLayer {
    val signed = (page - state.currentPage) - state.currentPageOffsetFraction
    val distance = abs(signed).coerceIn(0f, 1f)
    alpha = 1f - 0.65f * distance
    val s = 1f - 0.07f * distance
    scaleX = s
    scaleY = s
    rotationY = (signed * -18f).coerceIn(-18f, 18f)
    cameraDistance = 14f * density
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (signed > 0f) 0f else 1f, 0.5f)
}

@Composable
private fun CellGrid(cells: List<HomeCell>, rows: Int, config: LiquidThemeConfig, animateIn: Boolean, onFolder: (FolderState) -> Unit, onLaunch: (AppInfo) -> Unit, onMenu: (AppInfo) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until GRID_COLUMNS) {
                    val index = r * GRID_COLUMNS + c
                    val cell = cells.getOrNull(index)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        when (cell) {
                            is HomeCell.FolderCell -> LiquidFolderItem(title = cell.folder.title, apps = cell.folder.apps, config = config, onClick = { onFolder(cell.folder) })
                            is HomeCell.AppCell -> LiquidAppIconItem(app = cell.app, config = config, bob = config.iconBob, entranceIndex = if (animateIn) index else -1, onLongClick = { onMenu(cell.app) }, onClick = { onLaunch(cell.app) })
                            null -> Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPage(viewModel: LauncherViewModel, config: LiquidThemeConfig, onLaunch: (AppInfo) -> Unit, onMenu: (AppInfo) -> Unit) {
    val context = LocalContext.current
    val accent = config.accentColor()
    val alertsOn by NotificationHub.connected
    var memory by remember { mutableStateOf(readMemory(context)) }
    var storage by remember { mutableStateOf(readStorage()) }

    LaunchedEffect(Unit) {
        while (true) {
            memory = readMemory(context)
            storage = readStorage()
            delay(4000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Control Center", color = Color.White, fontFamily = FontFamily.SansSerif, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        MusicWidget(config = config, onEnableAccess = { viewModel.openSettingsScreen(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }, modifier = Modifier.fillMaxWidth().height(96.dp))

        Row(modifier = Modifier.fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeCard(title = "MEMORY", detail = "${gb(memory.used)} / ${gb(memory.total)} GB", fraction = UsageStatFraction(memory.used, memory.total), config = config, modifier = Modifier.weight(1f))
            GaugeCard(title = "STORAGE", detail = "${gb(storage.used)} / ${gb(storage.total)} GB", fraction = UsageStatFraction(storage.used, storage.total), config = config, modifier = Modifier.weight(1f))
        }

        SkyWidget(config = config, modifier = Modifier.fillMaxWidth().height(110.dp))

        SectionTitle("Quick tiles", accent)
        Row(modifier = Modifier.fillMaxWidth().height(78.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(Icons.Rounded.FlashlightOff, "Torch", viewModel.torchOn, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.toggleTorch() }
            QuickTile(Icons.Rounded.Settings, "Wi-Fi", false, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.openSettingsScreen(context, Settings.ACTION_WIFI_SETTINGS) }
            QuickTile(Icons.Rounded.Bluetooth, "Bluetooth", false, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.openSettingsScreen(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
            QuickTile(Icons.Rounded.Settings, "Settings", false, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.openSettingsScreen(context, Settings.ACTION_SETTINGS) }
        }
        Row(modifier = Modifier.fillMaxWidth().height(78.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(Icons.Rounded.NightsStay, "Focus", viewModel.focusMode, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.toggleFocus() }
            QuickTile(Icons.Rounded.AccessTime, "Idle", false, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.idleOpen = true }
            QuickTile(Icons.Rounded.Lock, "Lock", false, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.lockScreen(context) }
            QuickTile(Icons.Rounded.Notifications, "Alerts", alertsOn, config, Modifier.weight(1f).fillMaxHeight()) { viewModel.openSettingsScreen(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        }

        AppsRow("Suggested right now", viewModel.suggestedApps, "Open apps for a few days and the ones you use at this time of day appear here.", config, onLaunch, onMenu)
        AppsRow("Frequently used", viewModel.frequentApps, "Apps you open often will float up here.", config, onLaunch, onMenu)
    }
}

@Composable
private fun AppsRow(title: String, apps: List<AppInfo>, emptyText: String, config: LiquidThemeConfig, onLaunch: (AppInfo) -> Unit, onMenu: (AppInfo) -> Unit) {
    SectionTitle(title, config.accentColor())
    if (apps.isEmpty()) {
        Text(text = emptyText, color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.SansSerif, fontSize = 12.sp)
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 4) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    val app = apps.getOrNull(i)
                    if (app != null) {
                        LiquidAppIconItem(app = app, config = config, bob = config.iconBob, onLongClick = { onMenu(app) }, onClick = { onLaunch(app) })
                    }
                }
            }
        }
    }
}
