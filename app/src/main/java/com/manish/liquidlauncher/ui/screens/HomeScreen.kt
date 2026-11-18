package com.manish.liquidlauncher.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import com.manish.liquidlauncher.ui.components.BatteryWidget
import com.manish.liquidlauncher.ui.components.ClockWidget
import com.manish.liquidlauncher.ui.components.GaugeCard
import com.manish.liquidlauncher.ui.components.GlassIconButton
import com.manish.liquidlauncher.ui.components.LiquidAppIconItem
import com.manish.liquidlauncher.ui.components.LiquidFolderItem
import com.manish.liquidlauncher.ui.components.LiquidGlassSurface
import com.manish.liquidlauncher.ui.components.LiquidWallpaper
import com.manish.liquidlauncher.ui.components.QuickTile
import com.manish.liquidlauncher.ui.components.SectionTitle
import com.manish.liquidlauncher.ui.components.accentColor
import com.manish.liquidlauncher.ui.components.jellyTouch
import com.manish.liquidlauncher.ui.components.rememberBatteryInfo
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    onPickWallpaper: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val config = viewModel.themeConfig
    val accent = config.accentColor()
    val battery by rememberBatteryInfo()
    var layoutEditMode by remember { mutableStateOf(false) }

    // Page 0 = dashboard, page 1 = home, page 2+ = extra app pages (count is computed from screen size)
    var totalPages by remember { mutableIntStateOf(2) }
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { totalPages })

    val onLaunch: (AppInfo) -> Unit = { viewModel.launchApp(context, view, it) }
    val onMenu: (AppInfo) -> Unit = { viewModel.menuApp = it }

    // Home key / returning to launcher: close everything and glide back to the main page
    LaunchedEffect(viewModel.homeTick) {
        if (viewModel.homeTick > 0) pagerState.animateScrollToPage(1)
    }

    // Apps already in the dock are not repeated on the home grid
    val homeApps = remember(viewModel.installedApps, viewModel.dockApps) {
        val dock = viewModel.dockApps.map { it.packageName }.toSet()
        viewModel.installedApps.filter { it.packageName !in dock }
    }
    val folders = remember(homeApps) {
        val social = homeApps.filter { a -> SocialKeys.any { a.packageName.contains(it) } }
        val socialSet = social.map { it.packageName }.toSet()
        val google = homeApps.filter { a ->
            a.packageName !in socialSet && GoogleKeys.any { a.packageName.contains(it) }
        }
        listOfNotNull(
            if (social.size >= 2) FolderState("Social", social) else null,
            if (google.size >= 2) FolderState("Google", google) else null
        )
    }
    val looseApps = remember(homeApps, folders, viewModel.homeOrder) {
        val inFolders = folders.flatMap { f -> f.apps.map { it.packageName } }.toSet()
        val available = homeApps.filter { it.packageName !in inFolders }
        val byPackage = available.associateBy { it.packageName }
        val saved = viewModel.homeOrder.mapNotNull { byPackage[it] }
        saved + available.filter { it.packageName !in viewModel.homeOrder.toSet() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        // Swipe up = app drawer, swipe down = notification shade (One UI style)
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
            // ---- Top utility row ----------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassSurface(
                    config = config,
                    cornerRadius = 50.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .jellyTouch(haptics = config.haptics, pressX = 0.97f, pressY = 0.92f) {
                            viewModel.openDrawer()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search ${viewModel.installedApps.size} apps...",
                            color = Color.White.copy(alpha = 0.78f),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                GlassIconButton(Icons.Rounded.SwapHoriz, "Switch Default Launcher", config) {
                    viewModel.openDefaultLauncherPicker(context)
                }
                GlassIconButton(Icons.Rounded.Wallpaper, "Import Wallpaper", config, onClick = onPickWallpaper)
                GlassIconButton(Icons.Rounded.Tune, "Theme Engine", config, tint = accent) {
                    viewModel.isThemeEngineOpen = true
                }
                GlassIconButton(
                    icon = if (layoutEditMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                    contentDescription = if (layoutEditMode) "Finish layout editing" else "Edit home layout",
                    config = config,
                    tint = if (layoutEditMode) accent else Color.White
                ) { layoutEditMode = !layoutEditMode }
            }

            if (layoutEditMode) {
                Text(
                    text = "LAYOUT EDITOR  •  Long-press an app, then drag to reposition",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }

            // ---- Pager --------------------------------------------------------------------
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                val availableHeight = maxHeight
                val cellHeight = (60f * config.iconScale).dp + if (config.showLabels) 26.dp else 8.dp
                val rowGap = 14.dp
                val widgetBlock = 114.dp + 16.dp
                val rowsFull = rowsThatFit(availableHeight, cellHeight, rowGap)
                val rowsFirst = rowsThatFit(availableHeight - widgetBlock, cellHeight, rowGap)

                val firstCapacity = (rowsFirst * GRID_COLUMNS - folders.size).coerceAtLeast(0)
                val firstPageApps = looseApps.take(firstCapacity)
                val extraPages = looseApps.drop(firstCapacity).chunked(rowsFull * GRID_COLUMNS)

                SideEffect {
                    val wanted = 2 + extraPages.size
                    if (totalPages != wanted) totalPages = wanted
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pageTransform(pagerState, page)
                    ) {
                        when (page) {
                            0 -> DashboardPage(viewModel, config, onLaunch, onMenu)
                            1 -> Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(114.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ClockWidget(
                                        config = config,
                                        modifier = Modifier
                                            .weight(1.45f)
                                            .fillMaxHeight()
                                    )
                                    BatteryWidget(
                                        config = config,
                                        battery = battery,
                                        modifier = Modifier
                                            .weight(0.85f)
                                            .fillMaxHeight()
                                    )
                                }
                                val cells: List<HomeCell> = folders.map<FolderState, HomeCell> { HomeCell.FolderCell(it) } +
                                    firstPageApps.map<AppInfo, HomeCell> { HomeCell.AppCell(it) }
                                CellGrid(
                                    cells = cells,
                                    rows = rowsFirst,
                                    config = config,
                                    animateIn = true,
                                    editMode = layoutEditMode,
                                    appIndexOffset = 0,
                                    cellWidth = maxWidth / GRID_COLUMNS,
                                    cellHeight = cellHeight + rowGap,
                                    onFolder = { viewModel.openedFolder = it },
                                    onMoveApp = { from, to, packages -> viewModel.reorderHomeApps(from, to, looseApps.map { it.packageName }) },
                                    onLaunch = onLaunch,
                                    onMenu = onMenu
                                )
                            }
                            else -> CellGrid(
                                cells = extraPages.getOrElse(page - 2) { emptyList() }.map { HomeCell.AppCell(it) },
                                rows = rowsFull,
                                config = config,
                                animateIn = false,
                                editMode = layoutEditMode,
                                appIndexOffset = firstPageApps.size + (page - 2) * rowsFull * GRID_COLUMNS,
                                cellWidth = maxWidth / GRID_COLUMNS,
                                cellHeight = cellHeight + rowGap,
                                onFolder = { viewModel.openedFolder = it },
                                onMoveApp = { from, to, packages -> viewModel.reorderHomeApps(from, to, looseApps.map { it.packageName }) },
                                onLaunch = onLaunch,
                                onMenu = onMenu
                            )
                        }
                    }
                }
            }

            // ---- Page dots + dock ---------------------------------------------------------
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val selected = pagerState.currentPage == index
                        val dotWidth by animateDpAsState(
                            targetValue = if (selected) 20.dp else 6.dp,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(if (selected) accent else Color.White.copy(alpha = 0.35f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { scope.launch { pagerState.animateScrollToPage(index) } }
                        )
                    }
                }

                LiquidGlassSurface(
                    config = config,
                    cornerRadius = 32.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        viewModel.dockApps.forEach { app ->
                            LiquidAppIconItem(
                                app = app,
                                config = config,
                                showLabel = false,
                                iconBoxSize = (56f * config.iconScale).dp,
                                bob = config.iconBob,
                                onLongClick = { onMenu(app) },
                                onClick = { onLaunch(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rowsThatFit(available: Dp, cell: Dp, gap: Dp): Int =
    (((available + gap) / (cell + gap)).toInt()).coerceIn(1, 8)

/** Adjacent pages tilt and fade like glass panes. */
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
    transformOrigin = TransformOrigin(if (signed > 0f) 0f else 1f, 0.5f)
}

/** Fixed, never-overflowing grid (rows are computed from the real pager height). */
@Composable
private fun CellGrid(
    cells: List<HomeCell>,
    rows: Int,
    config: LiquidThemeConfig,
    animateIn: Boolean,
    editMode: Boolean,
    appIndexOffset: Int,
    cellWidth: Dp,
    cellHeight: Dp,
    onFolder: (FolderState) -> Unit,
    onMoveApp: (Int, Int, List<String>) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit
) {
    val appPackages = remember(cells) { cells.filterIsInstance<HomeCell.AppCell>().map { it.app.packageName } }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until GRID_COLUMNS) {
                    val index = r * GRID_COLUMNS + c
                    val cell = cells.getOrNull(index)
                    val appIndex = when (cell) {
                        is HomeCell.AppCell -> appIndexOffset + index - cells.indexOfFirst { it is HomeCell.AppCell }
                        else -> -1
                    }
                    var dragX by remember(cell) { mutableStateOf(0f) }
                    var dragY by remember(cell) { mutableStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight)
                            .graphicsLayer { translationX = dragX; translationY = dragY; scaleX = if (dragX != 0f || dragY != 0f) 1.06f else 1f; scaleY = if (dragX != 0f || dragY != 0f) 1.06f else 1f }
                            .then(if (editMode && appIndex >= 0) Modifier.pointerInput(appIndex, appPackages, cellWidth, cellHeight) {
                                var totalX = 0f
                                var totalY = 0f
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, amount ->
                                        change.consume()
                                        totalX += amount.x
                                        totalY += amount.y
                                        dragX = totalX
                                        dragY = totalY
                                    },
                                    onDragEnd = {
                                        val colShift = (totalX / cellWidth.toPx()).toInt()
                                        val rowShift = (totalY / cellHeight.toPx()).toInt()
                                        val localStart = index - cells.indexOfFirst { it is HomeCell.AppCell }
                                        val localTarget = (localStart + rowShift * GRID_COLUMNS + colShift).coerceIn(0, appPackages.lastIndex)
                                        val fromGlobal = appIndexOffset + localStart
                                        val toGlobal = appIndexOffset + localTarget
                                        if (localStart != localTarget) onMoveApp(fromGlobal, toGlobal, appPackages)
                                        dragX = 0f; dragY = 0f; totalX = 0f; totalY = 0f
                                    },
                                    onDragCancel = { dragX = 0f; dragY = 0f; totalX = 0f; totalY = 0f }
                                )
                            } else Modifier),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        when (cell) {
                            is HomeCell.FolderCell -> LiquidFolderItem(
                                title = cell.folder.title,
                                apps = cell.folder.apps,
                                config = config,
                                onClick = { onFolder(cell.folder) }
                            )
                            is HomeCell.AppCell -> LiquidAppIconItem(
                                app = cell.app,
                                config = config,
                                bob = config.iconBob,
                                entranceIndex = if (animateIn) index else -1,
                                onLongClick = { onMenu(cell.app) },
                                onClick = { onLaunch(cell.app) }
                            )
                            null -> Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Dashboard (left page): system gauges, quick tiles, frequently used apps
// -------------------------------------------------------------------------------------------------

private data class UsageStat(val used: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

private fun readMemory(context: Context): UsageStat {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return UsageStat(used = info.totalMem - info.availMem, total = info.totalMem)
}

private fun readStorage(): UsageStat {
    return try {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        UsageStat(used = total - free, total = total)
    } catch (_: Exception) {
        UsageStat(0, 0)
    }
}

private fun gb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_073_741_824.0)

@Composable
private fun DashboardPage(
    viewModel: LauncherViewModel,
    config: LiquidThemeConfig,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val accent = config.accentColor()
    var memory by remember { mutableStateOf(readMemory(context)) }
    var storage by remember { mutableStateOf(readStorage()) }

    LaunchedEffect(Unit) {
        while (true) {
            memory = readMemory(context)
            storage = readStorage()
            delay(4000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Control Center",
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GaugeCard(
                title = "MEMORY",
                detail = "${gb(memory.used)} / ${gb(memory.total)} GB",
                fraction = memory.fraction,
                config = config,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            GaugeCard(
                title = "STORAGE",
                detail = "${gb(storage.used)} / ${gb(storage.total)} GB",
                fraction = storage.fraction,
                config = config,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        SectionTitle("Quick tiles", accent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickTile(
                icon = if (viewModel.torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                label = "Torch",
                active = viewModel.torchOn,
                config = config,
                activeTint = Color(0xFFFFD54F),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { viewModel.toggleTorch() }
            QuickTile(Icons.Rounded.Wifi, "Wi-Fi", false, config, Modifier.weight(1f).fillMaxHeight()) {
                viewModel.openSettingsScreen(context, Settings.ACTION_WIFI_SETTINGS)
            }
            QuickTile(Icons.Rounded.Bluetooth, "Bluetooth", false, config, Modifier.weight(1f).fillMaxHeight()) {
                viewModel.openSettingsScreen(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            QuickTile(Icons.Rounded.Settings, "Settings", false, config, Modifier.weight(1f).fillMaxHeight()) {
                viewModel.openSettingsScreen(context, Settings.ACTION_SETTINGS)
            }
        }

        SectionTitle("Frequently used", accent)
        if (viewModel.frequentApps.isEmpty()) {
            Text(
                text = "Apps you open often will float up here.",
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until 4) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        val app = viewModel.frequentApps.getOrNull(i)
                        if (app != null) {
                            LiquidAppIconItem(
                                app = app,
                                config = config,
                                bob = config.iconBob,
                                onLongClick = { onMenu(app) },
                                onClick = { onLaunch(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}
