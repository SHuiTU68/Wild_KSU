package com.twj.wksu.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.twj.wksu.Natives
import com.twj.wksu.ksuApp
import com.twj.wksu.ui.screen.BottomBarDestination
import com.twj.wksu.ui.screen.FlashIt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.twj.wksu.ui.theme.AppTheme
import com.twj.wksu.ui.theme.KernelSUTheme
import com.twj.wksu.ui.theme.PRIMARY
import com.twj.wksu.ui.util.*
import com.twj.wksu.ui.viewmodel.ModuleViewModel
import com.twj.wksu.ui.viewmodel.SuperUserViewModel

data class ScrollState(
    val isScrollingDown: MutableState<Boolean>,
    val scrollOffset: MutableState<Float>,
    val previousScrollOffset: MutableState<Float>
)

val LocalScrollState = compositionLocalOf<ScrollState?> { null }

@Composable
fun rememberScrollConnection(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                
                // Update scroll offset
                val newOffset = scrollOffset.value + delta
                scrollOffset.value = newOffset
                
                // Calculate the scroll delta from previous offset
                val scrollDelta = previousScrollOffset.value - newOffset
                
                // Only update direction if scroll delta exceeds threshold
                if (abs(scrollDelta) > threshold) {
                    isScrollingDown.value = scrollDelta > 0
                    previousScrollOffset.value = newOffset
                }
                
                return Offset.Zero
            }
            
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Reset offset tracking after fling
                previousScrollOffset.value = scrollOffset.value
                return super.onPostFling(consumed, available)
            }
        }
    }
}

fun Modifier.horizontalSwipeNavigator(
    currentRoute: String?,
    destinations: List<BottomBarDestination>,
    onNavigate: (Int) -> Unit
): Modifier = pointerInput(currentRoute) {

    var totalDrag = 0f

    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            totalDrag += dragAmount
        },
        onDragEnd = {
            val threshold = 150f

            if (kotlin.math.abs(totalDrag) > threshold) {
                val currentIndex = destinations.indexOfFirst {
                    it.direction.route == currentRoute
                }

                if (currentIndex == -1) return@detectHorizontalDragGestures

                if (totalDrag < 0) {
                    val next = (currentIndex + 1)
                        .coerceAtMost(destinations.lastIndex)
                    if (next != currentIndex) onNavigate(next)
                } else {
                    val prev = (currentIndex - 1)
                        .coerceAtLeast(0)
                    if (prev != currentIndex) onNavigate(prev)
                }
            }
        }
    )
}

fun Modifier.trackScroll(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): Modifier {
    val scrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            
            // Update scroll offset
            val newOffset = scrollOffset.value + delta
            scrollOffset.value = newOffset
            
            // Calculate the scroll delta from previous offset
            val scrollDelta = previousScrollOffset.value - newOffset
            
            // Only update direction if scroll delta exceeds threshold
            if (abs(scrollDelta) > threshold) {
                isScrollingDown.value = scrollDelta > 0
                previousScrollOffset.value = newOffset
            }
            
            return Offset.Zero
        }
        
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Reset offset tracking after fling
            previousScrollOffset.value = scrollOffset.value
            return super.onPostFling(consumed, available)
        }
    }
    
    return this.nestedScroll(scrollConnection)
}

class MainActivity : ComponentActivity() {

    var zipUri by mutableStateOf<ArrayList<Uri>?>(null)
    enum class NavigateLocation { SUPERUSER, MODULES, SETTINGS }
    var navigateLoc by mutableStateOf<NavigateLocation?>(null)
    var moduleActionId by mutableStateOf<String?>(null)
    var amoledModeState = mutableStateOf(false)
    var appThemeState = mutableStateOf(AppTheme.AUTO)
    var appThemeCustomColorState = mutableIntStateOf(PRIMARY.toArgb())
    var wallpaperPaletteColor by mutableStateOf<Int?>(null)
    private val handler = Handler(Looper.getMainLooper())

    val moduleViewModel: ModuleViewModel by viewModels()
    val superUserViewModel: SuperUserViewModel by viewModels()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLanguage(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (superUserViewModel.appList.isEmpty()) {
                superUserViewModel.fetchAppList()
            }
            if (moduleViewModel.moduleList.isEmpty()) {
                moduleViewModel.fetchModuleList()
            }
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        try {
            val prefsInit = getSharedPreferences("settings", MODE_PRIVATE)
            appThemeState.value = AppTheme.fromValue(prefsInit.getInt("app_theme", 0))
            appThemeCustomColorState.intValue = prefsInit.getInt("theme_custom_color", PRIMARY.toArgb())
            amoledModeState.value = prefsInit.getBoolean("enable_amoled", false)
        } catch (_: Exception) {}
        try {
            LauncherIconManager.applySaved(this)
        } catch (_: Exception) {}

        val isManager = Natives.isManager
        if (isManager) install()

        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            intent.extras?.clear()
            intent = null
        }

        if(intent != null)
            handleIntent(intent)

        setContent {
            KernelSUTheme(
                appTheme = appThemeState.value,
                customColor = Color(appThemeCustomColorState.intValue),
                wallpaperColor = wallpaperPaletteColor
            ) {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current
                val prefs = remember {
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                }
                var backgroundSettings by remember {
                    mutableStateOf(
                        BackgroundSettings(
                            uri = prefs.getString("background_uri", null),
                            fillScreen = true,
                            isVideo = prefs.getBoolean("background_is_video", false)
                        )
                    )
                }
                var uiOverlaySettings by remember {
                    mutableStateOf(
                        UiOverlaySettings(
                            cardAlpha = run {
                                val transparencyPercent = if (prefs.contains("ui_card_transparency")) {
                                    prefs.getInt("ui_card_transparency", 0)
                                } else {
                                    100 - prefs.getInt("ui_card_alpha", 100)
                                }
                                (1f - (transparencyPercent.coerceIn(0, 100) / 100f))
                            },
                            dimAlpha = prefs.getInt("background_dim", 0) / 100f,
                        )
                    )
                }
                LaunchedEffect(backgroundSettings.uri, backgroundSettings.isVideo) {
                    val uri = backgroundSettings.uri
                    if (uri != null && !backgroundSettings.isVideo) {
                        wallpaperPaletteColor = withContext(Dispatchers.IO) {
                            runCatching {
                                val resolver = context.contentResolver
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                resolver.openInputStream(Uri.parse(uri))?.use {
                                    BitmapFactory.decodeStream(it, null, bounds)
                                }
                                var sample = 1
                                while (bounds.outWidth / sample > 512) sample *= 2
                                val bmp = resolver.openInputStream(Uri.parse(uri))?.use {
                                    BitmapFactory.decodeStream(
                                        it, null,
                                        BitmapFactory.Options().apply { inSampleSize = sample }
                                    )
                                } ?: return@runCatching null
                                val palette = Palette.from(bmp).maximumColorCount(16).generate()
                                val swatch = palette.vibrantSwatch
                                    ?: palette.dominantSwatch
                                    ?: palette.mutedSwatch
                                    ?: palette.darkVibrantSwatch
                                swatch?.rgb
                            }.getOrNull()
                        }
                    } else {
                        wallpaperPaletteColor = null
                    }
                }
                DisposableEffect(prefs) {
                    val listener =
                        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            if (key == "app_theme") {
                                appThemeState.value = AppTheme.fromValue(prefs.getInt("app_theme", 0))
                            }
                            if (key == "theme_custom_color") {
                                appThemeCustomColorState.intValue = prefs.getInt("theme_custom_color", PRIMARY.toArgb())
                            }
                            if (key == "background_uri" || key == "background_fill_screen" || key == "background_is_video") {
                                backgroundSettings = BackgroundSettings(
                                    uri = prefs.getString("background_uri", null),
                                    fillScreen = true,
                                    isVideo = prefs.getBoolean("background_is_video", false),
                                )
                            }
                            if (key == "ui_card_alpha" || key == "ui_card_transparency" || key == "background_dim") {
                                uiOverlaySettings = UiOverlaySettings(
                                    cardAlpha = run {
                                        val transparencyPercent = if (prefs.contains("ui_card_transparency")) {
                                            prefs.getInt("ui_card_transparency", 0)
                                        } else {
                                            100 - prefs.getInt("ui_card_alpha", 100)
                                        }
                                        (1f - (transparencyPercent.coerceIn(0, 100) / 100f))
                                    },
                                    dimAlpha = prefs.getInt("background_dim", 0) / 100f,
                                )
                            }
                        }
                    prefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose {
                        prefs.unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination
                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val navigator = navController.rememberDestinationsNavigator()

                val isManager = Natives.isManager
                val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                val homeDestination = BottomBarDestination.entries.firstOrNull()
                val startRoute = homeDestination?.direction?.route

                if (homeDestination != null && startRoute != null) {
                    BackHandler(enabled = currentRoute != startRoute && currentRoute in bottomBarRoutes) {
                        navigator.navigate(homeDestination.direction) {
                            popUpTo(NavGraphs.root) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // Track the last bottom bar destination index for directional animations
                var lastBottomBarIndex by remember { mutableStateOf(0) }
                var isBottomBarNavigation by remember { mutableStateOf(false) }
                
                // Scroll state for bottom bar visibility
                val isScrollingDown = remember { mutableStateOf(false) }
                val scrollOffset = remember { mutableStateOf(0f) }
                val previousScrollOffset = remember { mutableStateOf(0f) }
                
                // Remember the last valid navbar selection (persists across navbar hide/show)
                val lastValidNavbarSelection = remember { mutableStateOf(0) }

                LaunchedEffect(zipUri, navigateLoc, moduleActionId) {
                    if (moduleActionId != null) {
                        navigator.navigate(ExecuteModuleActionScreenDestination(moduleActionId!!))
                        moduleActionId = null
                    }

                    if (!zipUri.isNullOrEmpty()) {
                        val uris = zipUri!!
                        val component = intent?.component?.className
                        val flashIt = when {
                            component?.endsWith("FlashAnyKernel") == true -> FlashIt.FlashAnyKernel(uris.first())
                            else -> FlashIt.FlashModules(uris)
                        }
                        
                        navigator.navigate(
                            FlashScreenDestination(flashIt = flashIt)
                        )
                        zipUri = null
                    }

                    if (zipUri.isNullOrEmpty() && navigateLoc != null) {
                        when (navigateLoc) {
                            NavigateLocation.SUPERUSER -> navigator.navigate(SuperUserScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            NavigateLocation.MODULES -> navigator.navigate(ModuleScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            NavigateLocation.SETTINGS -> navigator.navigate(SettingScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            else -> { /* no-op for exhaustiveness */ }
                        }
                        navigateLoc = null
                    }
                }

                val showBottomBar = when (currentDestination?.route) {
                    FlashScreenDestination.route -> false // Hide for FlashScreenDestination
                    ExecuteModuleActionScreenDestination.route -> false // Hide for ExecuteModuleActionScreen
                    else -> true
                }

                val baseScheme = MaterialTheme.colorScheme
                val cardAlpha = uiOverlaySettings.cardAlpha.coerceIn(0f, 1f)
                val scheme = remember(baseScheme, cardAlpha) {
                    baseScheme.copy(
                        surface = baseScheme.surface.copy(alpha = cardAlpha),
                        surfaceVariant = baseScheme.surfaceVariant.copy(alpha = cardAlpha),
                        surfaceContainerLowest = baseScheme.surfaceContainerLowest.copy(alpha = cardAlpha),
                        surfaceContainerLow = baseScheme.surfaceContainerLow.copy(alpha = cardAlpha),
                        surfaceContainer = baseScheme.surfaceContainer.copy(alpha = cardAlpha),
                        surfaceContainerHigh = baseScheme.surfaceContainerHigh.copy(alpha = cardAlpha),
                        surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(alpha = cardAlpha),
                        primaryContainer = baseScheme.primaryContainer.copy(alpha = cardAlpha),
                        secondaryContainer = baseScheme.secondaryContainer.copy(alpha = cardAlpha),
                        tertiaryContainer = baseScheme.tertiaryContainer.copy(alpha = cardAlpha),
                        errorContainer = baseScheme.errorContainer.copy(alpha = cardAlpha),
                        outlineVariant = baseScheme.outlineVariant.copy(alpha = cardAlpha),
                    )
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalSnackbarHost provides snackBarHostState,
                            LocalScrollState provides ScrollState(
                                isScrollingDown = isScrollingDown,
                                scrollOffset = scrollOffset,
                                previousScrollOffset = previousScrollOffset
                            ),
                            LocalBackgroundSettings provides backgroundSettings,
                            LocalUiOverlaySettings provides uiOverlaySettings,
                        ) {
                            CompositionLocalProvider(LocalBaseColorScheme provides baseScheme) {
                                MaterialTheme(
                                    colorScheme = scheme,
                                    typography = MaterialTheme.typography,
                                    shapes = MaterialTheme.shapes,
                                ) {
                                AppBackground(modifier = Modifier.fillMaxSize())
                            val visibleDestinations = remember(fullFeatured) {
                                BottomBarDestination.entries.filter { fullFeatured || !it.rootRequired }
                            }

                            fun navigateToIndex(index: Int) {
                                val destination = visibleDestinations.getOrNull(index) ?: return
                                if (destination.direction.route == currentRoute) return

                                navigator.navigate(destination.direction) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }

                            val hostModifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .horizontalSwipeNavigator(
                                    currentRoute = currentRoute,
                                    destinations = visibleDestinations,
                                    onNavigate = { navigateToIndex(it) }
                                )

                            DestinationsNavHost(
                                modifier = hostModifier,
                                navGraph = NavGraphs.root,
                                navController = navController,
                                defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                                    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar: slide based on index direction
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                                slideInHorizontally(initialOffsetX = { it * offsetSign }, animationSpec = tween(300))
                                            }
                                            // Detail page → bottom bar: slide in from left
                                            targetRoute in bottomBarRoutes && initialRoute !in bottomBarRoutes -> {
                                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                                            }
                                            // Bottom bar → detail page: slide in from right
                                            else -> slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                                        }
                                    }

                                    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar: slide out opposite direction
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                                slideOutHorizontally(targetOffsetX = { it * offsetSign }, animationSpec = tween(300))
                                            }
                                            // Bottom bar → detail page: slide out to left
                                            initialRoute in bottomBarRoutes && targetRoute !in bottomBarRoutes -> {
                                                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                                            }
                                            // Default
                                            else -> slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                                        }
                                    }

                                    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar pop: mirror of exit
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                                slideInHorizontally(initialOffsetX = { it * offsetSign }, animationSpec = tween(300))
                                            }
                                            // Returning from detail → bottom bar: slide in from left
                                            targetRoute in bottomBarRoutes -> {
                                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                                            }
                                            else -> slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                                        }
                                    }

                                    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                        val targetRoute = targetState.destination.route
                                        val initialRoute = initialState.destination.route

                                        val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                        val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                        when {
                                            // Bottom bar → bottom bar pop
                                            targetIndex != -1 && initialIndex != -1 -> {
                                                val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                                slideOutHorizontally(targetOffsetX = { it * offsetSign }, animationSpec = tween(300))
                                            }
                                            // Detail page closing: slide out to right
                                            initialRoute !in bottomBarRoutes -> {
                                                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                                            }
                                            else -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                                        }
                                    }
                                }
                            )
                                }
                            }
                        }
                        
                        // Floating Bottom Bar as overlay
                        AnimatedVisibility(
                            visible = showBottomBar,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            BottomBar(navController, lastValidNavbarSelection)
                        }
                    }
                }
            }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putBoolean("enable_amoled", enabled).apply()
        } catch (_: Exception) {}
        amoledModeState.value = enabled
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val shortcutType = intent.getStringExtra("shortcut_type")
        if (shortcutType == "module_action") {
            moduleActionId = intent.getStringExtra("module_id")
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                zipUri =
                    intent.data?.let { arrayListOf(it) }
                        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra("uris", Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra("uris")
                        }
            }

            "ACTION_SETTINGS" -> navigateLoc = NavigateLocation.SETTINGS
            "ACTION_SUPERUSER" -> navigateLoc = NavigateLocation.SUPERUSER
            "ACTION_MODULES" -> navigateLoc = NavigateLocation.MODULES
            else -> { /* ignore other actions */ }
        }
    }
}

@Composable
private fun BottomBar(
    navController: NavHostController,
    lastValidSelection: MutableState<Int>
) {
    val navigator = navController.rememberDestinationsNavigator()
    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()

    val visibleDestinations = remember(fullFeatured) {
        BottomBarDestination.entries.filter { fullFeatured || !it.rootRequired }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val isOnBackStack = visibleDestinations.map { destination ->
        navController.isRouteOnBackStackAsState(destination.direction).value
    }

    val selectedIndex = run {
        val exactMatch = visibleDestinations.indexOfFirst { it.direction.route == currentRoute }
        if (exactMatch != -1) exactMatch
        else isOnBackStack.indexOfLast { it }
    }

    if (selectedIndex != -1) lastValidSelection.value = selectedIndex
    val effectiveSelectedIndex = if (selectedIndex != -1) selectedIndex else lastValidSelection.value

    // Drag state
    var isDraggingPill by remember { mutableStateOf(false) }
    var dragTargetIndex by remember { mutableStateOf(effectiveSelectedIndex) }

    // During drag, animate toward dragTargetIndex; otherwise animate toward effectiveSelectedIndex
    val animatedSelectedIndex by animateFloatAsState(
        targetValue = (if (isDraggingPill) dragTargetIndex else effectiveSelectedIndex).toFloat(),
        animationSpec = if (isDraggingPill) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "selectedIndex"
    )

    fun navigateToIndex(index: Int) {
        val destination = visibleDestinations.getOrNull(index) ?: return
        if (destination.direction.route == currentRoute) return
        navigator.navigate(destination.direction) {
            popUpTo(NavGraphs.root.startRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
            )
    ) {
        val screenWidth = maxWidth
        val horizontalScreenPadding = when {
            screenWidth > 600.dp -> 32.dp
            screenWidth > 400.dp -> 24.dp
            else -> 16.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalScreenPadding, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.wrapContentWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                val itemSize = 56.dp
                val itemSpacing = 4.dp
                val containerPadding = 7.dp

                val navBarWidth = (itemSize * visibleDestinations.size) +
                        (itemSpacing * (visibleDestinations.size - 1)) +
                        (containerPadding * 2)

                val density = LocalDensity.current
                val itemSizePx = with(density) { itemSize.toPx() }
                val itemSpacingPx = with(density) { itemSpacing.toPx() }
                val containerPaddingPx = with(density) { containerPadding.toPx() }

                Box(
                    modifier = Modifier
                        .width(navBarWidth)
                        .height(72.dp)
                        .pointerInput(visibleDestinations, effectiveSelectedIndex) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val extraTouchArea = with(density) { 20.dp.toPx() }

                                    val pillLeft = containerPaddingPx +
                                            effectiveSelectedIndex * (itemSizePx + itemSpacingPx) - extraTouchArea

                                    val pillRight = pillLeft + itemSizePx + (extraTouchArea * 2)

                                    if (offset.x in pillLeft..pillRight) {
                                        isDraggingPill = true
                                        dragTargetIndex = effectiveSelectedIndex
                                    }
                                },
                                onDragEnd = {
                                    if (isDraggingPill) {
                                        navigateToIndex(dragTargetIndex)
                                        isDraggingPill = false
                                    }
                                },
                                onDragCancel = {
                                    isDraggingPill = false
                                },
                                onDrag = { change, _ ->
                                    if (isDraggingPill) {
                                        change.consume()
                                        // Map finger X to nearest icon index
                                        val index = ((change.position.x - containerPaddingPx) /
                                                (itemSizePx + itemSpacingPx))
                                            .toInt()
                                            .coerceIn(0, visibleDestinations.lastIndex)
                                        dragTargetIndex = index
                                    }
                                }
                            )
                        }
                ) {
                    var totalWidth by remember { mutableStateOf(0) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = containerPadding)
                            .onSizeChanged { totalWidth = it.width }
                    ) {
                        // Sliding pill indicator
                        if (totalWidth > 0 && visibleDestinations.isNotEmpty()) {
                            val indicatorOffset = (itemSizePx + itemSpacingPx) * animatedSelectedIndex

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp)
                                    .offset {
                                        IntOffset(x = indicatorOffset.toInt(), y = 0)
                                    }
                                    .width(itemSize)
                                    // Subtle scale-up when dragging, like iOS
                                    .graphicsLayer {
                                        scaleX = if (isDraggingPill) 1.1f else 1f
                                        scaleY = if (isDraggingPill) 1.1f else 1f
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(itemSize)
                                        .background(
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                )
                            }
                        }
                        // Navigation items
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleDestinations.forEachIndexed { index, destination ->
                                val isSelected = index == (if (isDraggingPill) dragTargetIndex else effectiveSelectedIndex)

                                Box(
                                    modifier = Modifier
                                        .size(itemSize)
                                        .clip(MaterialTheme.shapes.large)
                                        .clickable {
                                            if (destination.direction.route == currentRoute) return@clickable
                                            navigateToIndex(index)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isSelected) destination.iconSelected else destination.iconNotSelected,
                                        stringResource(destination.label),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
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
@Composable
private fun AppBackground(modifier: Modifier = Modifier) {
    val backgroundSettings = LocalBackgroundSettings.current
    val uiOverlaySettings = LocalUiOverlaySettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseColor = MaterialTheme.colorScheme.background

    val contentScale = if (backgroundSettings.fillScreen) {
        ContentScale.Crop
    } else {
        ContentScale.Fit
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor)
        )

    val uri = backgroundSettings.uri
    if (uri != null) {
            if (backgroundSettings.isVideo) {
                VideoBackground(
                    uri = uri,
                    fillScreen = backgroundSettings.fillScreen,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(android.net.Uri.parse(uri))
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }

            val dimAlpha = uiOverlaySettings.dimAlpha.coerceIn(0f, 1f)
            if (dimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAlpha))
                )
            }
        }
    }
}

@Composable
private fun VideoBackground(
    uri: String,
    fillScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                playWhenReady = true
                setMediaItem(MediaItem.fromUri(android.net.Uri.parse(uri)))
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                player = exoPlayer
                resizeMode = if (fillScreen) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        },
        update = { view ->
            view.resizeMode = if (fillScreen) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    )
}