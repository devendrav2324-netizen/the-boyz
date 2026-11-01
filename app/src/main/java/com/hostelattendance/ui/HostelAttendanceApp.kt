package com.hostelattendance.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import com.hostelattendance.HostelAttendanceApplication

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.styropyr0.prismal.depth.PrismalDepthShadow
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.specular.PrismalSpecular
import com.styropyr0.prismal.specular.PrismalSpecularStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hostelattendance.BuildConfig
import com.hostelattendance.auth.AccountAccess
import com.hostelattendance.auth.FirebaseAccountRepository
import com.hostelattendance.R
import kotlinx.coroutines.launch

private data class AppDestination(
    val route: String,
    val label: String,
    val icon: DestinationIcon,
)

private enum class DestinationIcon {
    ATTENDANCE,
    REPORTS,
    REGISTRATION,
    SETTINGS,
}

private val allNavRoutes = listOf("attendance", "reports", "registration", "settings")

@Composable
fun HostelAttendanceApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? Activity
    val accountRepository = remember(context) { FirebaseAccountRepository(context) }
    val accountAccess by accountRepository.access.collectAsState()
    val scope = rememberCoroutineScope()

    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(accountRepository) {
        accountRepository.refreshAccess()
    }

    val approvedAccount = (accountAccess as? AccountAccess.Approved)?.account
    val activeHostelId = approvedAccount?.hostelId ?: "cgitr-boys-hostel"
    val allowedWings = approvedAccount?.allowedWingIds ?: emptySet()
    val isAdmin = approvedAccount?.role == "ADMIN"
    val canRegister = isAdmin && (approvedAccount?.canRegisterStudents ?: false)

    val destinations = remember(isAdmin) {
        if (isAdmin) {
            listOf(
                AppDestination("attendance", "Attendance", DestinationIcon.ATTENDANCE),
                AppDestination("reports", "Reports", DestinationIcon.REPORTS),
                AppDestination("registration", "Registration", DestinationIcon.REGISTRATION),
                AppDestination("settings", "Settings", DestinationIcon.SETTINGS),
            )
        } else {
            listOf(
                AppDestination("attendance", "Attendance", DestinationIcon.ATTENDANCE),
                AppDestination("reports", "Reports", DestinationIcon.REPORTS),
                AppDestination("settings", "Settings", DestinationIcon.SETTINGS),
            )
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigation = destinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    LaunchedEffect(activeHostelId, approvedAccount?.uid) {
        if (approvedAccount == null) return@LaunchedEffect
        val app = context.applicationContext as? HostelAttendanceApplication ?: return@LaunchedEffect

        // 1. Restore wings & students from Cloud Firestore first if available
        val (restoredWings, restoredStudents) = com.hostelattendance.data.sync.FirestoreSyncService.restoreWingsAndStudentsFromFirestore(
            activeHostelId,
            app.database.wingDao(),
            app.database.studentDao(),
        )

        // 2. Fallback: If local database still has 0 students (e.g. fresh install, offline, or cloud empty), seed roster
        val currentStudents = app.database.studentDao().getAllStudents(activeHostelId)
        if (currentStudents.isEmpty()) {
            com.hostelattendance.data.seed.DatabaseSeeder.seedIfNeeded(context, app.database, activeHostelId)
        }

        // 3. Admins upload seeded master data if cloud was empty
        if (isAdmin && (restoredWings == 0 || restoredStudents == 0)) {
            val allWings = app.database.wingDao().getAllActiveWings(activeHostelId)
            val allStudents = app.database.studentDao().getAllStudents(activeHostelId)
            if (allWings.isNotEmpty() || allStudents.isNotEmpty()) {
                com.hostelattendance.data.sync.FirestoreSyncService.syncWingsAndStudents(
                    activeHostelId,
                    allWings,
                    allStudents,
                )
            }
        }

        // 4. Restore past attendance records & sessions from Cloud Firestore into local Room SQLite
        com.hostelattendance.data.sync.FirestoreSyncService.restoreAttendanceFromFirestore(
            activeHostelId,
            app.database.attendanceSessionDao(),
        )

        // 5. Ensure app update release metadata is published in Cloud Firestore
        if (isAdmin) {
            try {
                com.hostelattendance.data.update.AppUpdateManager.publishUpdateMetadata()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(activeHostelId, approvedAccount?.uid) {
        if (approvedAccount == null) return@DisposableEffect onDispose {}
        val app = context.applicationContext as? HostelAttendanceApplication ?: return@DisposableEffect onDispose {}

        val reg = com.hostelattendance.data.sync.FirestoreSyncService.listenToWingsAndStudentsRealtime(
            hostelId = activeHostelId,
            wingDao = app.database.wingDao(),
            studentDao = app.database.studentDao(),
        )

        onDispose {
            reg?.remove()
        }
    }

    LaunchedEffect(accountAccess) {
        if (accountAccess is AccountAccess.Approved && currentDestination?.route == "login") {
            navController.navigate("attendance") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    var lastBackPressTimeMillis by remember { mutableStateOf(0L) }

    // Intercept back button across any section in the app: double-back press opens Quit dialog
    BackHandler(enabled = showNavigation) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTimeMillis < 2000L) {
            showExitDialog = true
            lastBackPressTimeMillis = 0L
        } else {
            lastBackPressTimeMillis = currentTime
            android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val destinationIndex = destinations.indexOfFirst { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }.coerceAtLeast(0)

    var optimisticIndex by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(destinationIndex) {
        optimisticIndex = destinationIndex
    }

    val globalGlassBackdrop = rememberPrismalGlassLayer()

    Box(modifier = Modifier.fillMaxSize()) {
        // Full visibility video background without any blur effect or shade effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .prismalGlassLayer(globalGlassBackdrop)
        ) {
            BackgroundVideoPlayer(
                videoResId = R.raw.app_bg_video,
                modifier = Modifier.fillMaxSize(),
            )
        }

        CompositionLocalProvider(LocalGlobalGlassBackdrop provides globalGlassBackdrop) {
            Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (showNavigation) {
                    AppTopBar(
                        role = approvedAccount?.role ?: "USER",
                    )
                }
            },
            bottomBar = {
                if (showNavigation) {
                    SegmentedNavigationBar(
                        destinations = destinations,
                        selectedIndex = optimisticIndex,
                        onDestinationSelected = { destination ->
                            val targetIndex = destinations.indexOf(destination)
                            if (targetIndex == optimisticIndex && currentDestination?.route == destination.route) {
                                return@SegmentedNavigationBar
                            }
                            optimisticIndex = targetIndex
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "login",
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    val initialRoute = initialState.destination.route
                    val targetRoute = targetState.destination.route
                    val initialIndex = allNavRoutes.indexOf(initialRoute)
                    val targetIndex = allNavRoutes.indexOf(targetRoute)

                    if (initialIndex >= 0 && targetIndex >= 0 && initialIndex != targetIndex) {
                        val movingRight = targetIndex > initialIndex
                        val initialOffset = if (movingRight) { w: Int -> (w * 0.12f).toInt() } else { w: Int -> -(w * 0.12f).toInt() }
                        slideInHorizontally(
                            initialOffsetX = initialOffset,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)) +
                            scaleIn(initialScale = 0.965f, animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing))
                    } else {
                        fadeIn(tween(durationMillis = 350, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
                    }
                },
                exitTransition = {
                    val initialRoute = initialState.destination.route
                    val targetRoute = targetState.destination.route
                    val initialIndex = allNavRoutes.indexOf(initialRoute)
                    val targetIndex = allNavRoutes.indexOf(targetRoute)

                    if (initialIndex >= 0 && targetIndex >= 0 && initialIndex != targetIndex) {
                        val movingRight = targetIndex > initialIndex
                        val targetOffset = if (movingRight) { w: Int -> -(w * 0.08f).toInt() } else { w: Int -> (w * 0.08f).toInt() }
                        slideOutHorizontally(
                            targetOffsetX = targetOffset,
                            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                        ) + fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)) +
                            scaleOut(targetScale = 0.965f, animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing))
                    } else {
                        fadeOut(tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.96f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                    }
                },
                popEnterTransition = {
                    val initialRoute = initialState.destination.route
                    val targetRoute = targetState.destination.route
                    val initialIndex = allNavRoutes.indexOf(initialRoute)
                    val targetIndex = allNavRoutes.indexOf(targetRoute)

                    if (initialIndex >= 0 && targetIndex >= 0 && initialIndex != targetIndex) {
                        val movingRight = targetIndex > initialIndex
                        val initialOffset = if (movingRight) { w: Int -> (w * 0.12f).toInt() } else { w: Int -> -(w * 0.12f).toInt() }
                        slideInHorizontally(
                            initialOffsetX = initialOffset,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)) +
                            scaleIn(initialScale = 0.965f, animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing))
                    } else {
                        fadeIn(tween(durationMillis = 350, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
                    }
                },
                popExitTransition = {
                    val initialRoute = initialState.destination.route
                    val targetRoute = targetState.destination.route
                    val initialIndex = allNavRoutes.indexOf(initialRoute)
                    val targetIndex = allNavRoutes.indexOf(targetRoute)

                    if (initialIndex >= 0 && targetIndex >= 0 && initialIndex != targetIndex) {
                        val movingRight = targetIndex > initialIndex
                        val targetOffset = if (movingRight) { w: Int -> -(w * 0.08f).toInt() } else { w: Int -> (w * 0.08f).toInt() }
                        slideOutHorizontally(
                            targetOffsetX = targetOffset,
                            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                        ) + fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)) +
                            scaleOut(targetScale = 0.965f, animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing))
                    } else {
                        fadeOut(tween(durationMillis = 300, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.96f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                    }
                },
            ) {
                composable("login") {
                    AccessGate(
                        access = accountAccess,
                        isConfigured = accountRepository.isConfigured(),
                        onGoogleSignIn = {
                            scope.launch { accountRepository.signInWithGoogle(context) }
                        },
                        onRetry = { scope.launch { accountRepository.refreshAccess() } },
                        onSignOut = { scope.launch { accountRepository.signOut() } },
                        onEnterApp = {
                            navController.navigate("attendance") {
                                popUpTo("login") { inclusive = true }
                            }
                        },
                    )
                }
                composable("attendance") {
                    AttendanceScreen(
                        hostelId = activeHostelId,
                        allowedWingIds = allowedWings,
                        isAdmin = isAdmin,
                        onNavigateToRegistration = {
                            navController.navigate("registration") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable("reports") {
                    ReportsScreen(
                        hostelId = activeHostelId,
                        isAdmin = isAdmin,
                    )
                }
                composable("registration") {
                    if (!isAdmin) {
                        LaunchedEffect(Unit) {
                            navController.navigate("attendance") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    } else {
                        RegistrationScreen(
                            hostelId = activeHostelId,
                            canRegisterStudents = canRegister,
                            isAdmin = isAdmin,
                        )
                    }
                }
                composable("settings") {
                    val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                        ?: approvedAccount?.uid.orEmpty()
                    SettingsScreen(
                        hostelId = activeHostelId,
                        role = approvedAccount?.role ?: "USER",
                        userEmail = userEmail,
                        onSignOut = {
                            scope.launch {
                                accountRepository.signOut()
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

        if (showExitDialog) {
            AppleGlassDialog(
                title = "Quit the app",
                message = "Are you sure you want to exit the application?",
                confirmText = "Yes",
                dismissText = "No",
                isDestructive = true,
                onConfirm = {
                    showExitDialog = false
                    activity?.finish()
                },
                onDismiss = {
                    showExitDialog = false
                },
            )
        }
    }
}

@Composable
private fun AppTopBar(
    role: String,
) {
    val backdrop = LocalGlobalGlassBackdrop.current
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ethereal liquid glass branding pill with live active indicator
        Box(
            modifier = Modifier
                .liquidGlassCapsuleEffect(
                    backdrop = backdrop,
                    density = density,
                    surfaceColor = Color(0x4C161324),
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color(0xFF00E676)),
                )
                Text(
                    text = "THE BOYZ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                    ),
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        // Refined role badge pill
        Box(
            modifier = Modifier
                .liquidGlassCapsuleEffect(
                    backdrop = backdrop,
                    density = density,
                    surfaceColor = Color(0x4C161324),
                )
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val roleColor = if (role.equals("ADMIN", ignoreCase = true)) {
                    Color(0xFFCE93D8)
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(roleColor),
                )
                Text(
                    text = role.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = roleColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Premium Animated Segmented Navigation Bar
 * Recreates the Apple-inspired minimalistic tactile design:
 * - Light grey (#F1F1F1) pill track with soft ambient shadows.
 * - Floating black gradient (#252525 to #050505) selected pill.
 * - Upper glossy rim highlight and dynamic physical drop shadow.
 * - Slow, fluid Apple spring glide (650–850ms) without clipping or jumps.
 * - Synchronized text color transition from medium grey (#777777) to white.
 */
@Composable
private fun SegmentedNavigationBar(
    destinations: List<AppDestination>,
    selectedIndex: Int,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val backdrop = LocalGlobalGlassBackdrop.current
    val density = LocalDensity.current
    val trackShape = remember { PrismalRoundedRectangle(36.dp) }
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val trackModifier = if (backdrop != null) {
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawPrismalGlass(
                    backdrop = backdrop,
                    shape = { trackShape },
                    effects = {
                        applyPrismalGlassEffects(
                            density = density,
                            adaptiveLuminance = false,
                            luminance = 0.5f,
                            blurRadiusPx = with(density) { 20.dp.toPx() },
                            refractionHeightPx = 18f,
                            refractionAmountPx = 26f,
                            saturation = 1.30f,
                            depthEffect = true,
                            chromaticAberration = 0.10f,
                            useVibrancy = true,
                        )
                    },
                    specular = {
                        PrismalSpecular(
                            width = 1.dp,
                            alpha = 0.85f,
                            style = PrismalSpecularStyle.Ambient,
                        )
                    },
                    depthShadow = {
                        PrismalDepthShadow(
                            radius = 18.dp,
                            color = Color.Black.copy(alpha = 0.32f),
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color(0x40181524))
                    },
                )
                .padding(horizontal = 5.dp, vertical = 5.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.50f),
                )
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0xB3181524))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.12f),
                            Color(0x15FFFFFF),
                        ),
                    ),
                    shape = RoundedCornerShape(36.dp),
                )
                .padding(horizontal = 5.dp, vertical = 5.dp)
        }

        Box(modifier = trackModifier) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = maxWidth
                val tabCount = destinations.size
                val tabWidth = totalWidth / tabCount
                val targetOffset = tabWidth * selectedIndex

                // Fluid Apple-like smooth & slow spring glide (750–850ms, butter-smooth motion)
                val indicatorOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 115f, // Smooth, slow, elegant and continuous glide
                    ),
                    label = "segmentedPillOffset",
                )

                // Depth & shadow elevates slightly during movement
                val isGliding = kotlin.math.abs((indicatorOffset - targetOffset).value) > 1.2f
                val pillElevation by animateDpAsState(
                    targetValue = if (isGliding) 14.dp else 8.dp,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 115f,
                    ),
                    label = "pillElevation",
                )

                // Floating Black Selected Pill Indicator with Liquid Glass
                val pillModifier = if (backdrop != null) {
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .liquidGlassCapsuleEffect(
                            backdrop = backdrop,
                            density = density,
                            blurRadius = 14.dp,
                            refractionHeightPx = 14f,
                            refractionAmountPx = 20f,
                            surfaceColor = Color(0xD916141D),
                            specular = PrismalSpecular(
                                width = 1.dp,
                                alpha = 0.90f,
                                style = PrismalSpecularStyle.Ambient,
                            ),
                            depthShadow = PrismalDepthShadow(
                                radius = pillElevation,
                                color = Color.Black.copy(alpha = 0.55f),
                            ),
                        )
                } else {
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .shadow(
                            elevation = pillElevation,
                            shape = pillShape,
                            ambientColor = Color.Black.copy(alpha = 0.40f),
                            spotColor = Color.Black.copy(alpha = 0.65f),
                        )
                        .clip(pillShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF282828),
                                    Color(0xFF141414),
                                    Color(0xFF050505),
                                ),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.32f),
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent,
                                ),
                            ),
                            shape = pillShape,
                        )
                }

                Box(modifier = pillModifier) {
                    // Subtle glossy highlight on the upper surface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(pillShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.16f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }

                // Navigation Items Row
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isSelected = selectedIndex == index
                        val interactionSource = rememberMotionInteractionSource()

                        // Gentle scale feedback
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = spring(
                                dampingRatio = 0.82f,
                                stiffness = 160f,
                            ),
                            label = "${destination.route}IconScale",
                        )

                        // Synchronized text color transition from #777777 to White
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF777777),
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            label = "${destination.route}ContentColor",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pillShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onDestinationSelected(destination) },
                                )
                                .pressFeedback(interactionSource, pressedScale = 0.94f)
                                .semantics {
                                    this.selected = isSelected
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            ) {
                                NavigationGlyph(
                                    icon = destination.icon,
                                    color = contentColor,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = destination.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationGlyph(
    icon: DestinationIcon,
    color: Color,
) {
    if (icon == DestinationIcon.SETTINGS) {
        val isSelected = color == Color.White
        val iconRes = if (isSelected) {
            com.hostelattendance.R.drawable.ic_nav_settings_icons8_active
        } else {
            com.hostelattendance.R.drawable.ic_nav_settings_icons8
        }
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        return
    }

    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        when (icon) {
            DestinationIcon.ATTENDANCE -> {
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 0.55f)
                    lineTo(w * 0.40f, h * 0.80f)
                    lineTo(w * 0.85f, h * 0.25f)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            DestinationIcon.REPORTS -> {
                val barWidth = w * 0.2f
                val gap = w * 0.1f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.1f, h * 0.55f),
                    size = Size(barWidth, h * 0.35f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.1f + barWidth + gap, h * 0.25f),
                    size = Size(barWidth, h * 0.65f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.1f + (barWidth + gap) * 2, h * 0.40f),
                    size = Size(barWidth, h * 0.50f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }
            DestinationIcon.REGISTRATION -> {
                drawCircle(
                    color = color,
                    radius = w * 0.22f,
                    center = Offset(w * 0.5f, h * 0.30f),
                    style = Stroke(width = strokeWidth),
                )
                val bodyPath = Path().apply {
                    moveTo(w * 0.2f, h * 0.85f)
                    cubicTo(w * 0.2f, h * 0.60f, w * 0.8f, h * 0.60f, w * 0.8f, h * 0.85f)
                }
                drawPath(
                    path = bodyPath,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            DestinationIcon.SETTINGS -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                drawCircle(
                    color = color,
                    radius = w * 0.18f,
                    center = Offset(cx, cy),
                    style = Stroke(width = strokeWidth),
                )
                for (i in 0 until 6) {
                    val angle = (i * 60f) * (Math.PI.toFloat() / 180f)
                    val rInner = w * 0.28f
                    val rOuter = w * 0.44f
                    drawLine(
                        color = color,
                        start = Offset(cx + kotlin.math.cos(angle) * rInner, cy + kotlin.math.sin(angle) * rInner),
                        end = Offset(cx + kotlin.math.cos(angle) * rOuter, cy + kotlin.math.sin(angle) * rOuter),
                        strokeWidth = strokeWidth * 1.4f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessGate(
    access: AccountAccess,
    isConfigured: Boolean,
    onGoogleSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onEnterApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.hostel_logo),
            contentDescription = "Hostel Attendance logo",
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(width = 82.dp, height = 114.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "CGITR BOYS HOSTEL",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Hostel Attendance",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )

        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AnimatedContent(
                targetState = access,
                transitionSpec = {
                    fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "accessGateContent",
            ) { targetAccess ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (targetAccess) {
                        AccountAccess.Checking -> {
                            AnimatedSweepSpinner(
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                            )
                            Text(
                                text = "Checking account access…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        AccountAccess.SetupRequired -> {
                            Text(
                                text = "Sign-in is not configured",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Enable Google sign-in in Firebase Authentication, add this app's SHA-1 fingerprint in Firebase project settings, then replace google-services.json with the updated file containing a web OAuth client.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AccountAccess.SignedOut -> {
                            Text(
                                text = "Sign in to your hostel",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Use an authorized Google account to access attendance records and management tools.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val interactionSource = rememberMotionInteractionSource()
                            Button(
                                onClick = onGoogleSignIn,
                                enabled = isConfigured,
                                interactionSource = interactionSource,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .pressFeedback(interactionSource),
                            ) {
                                Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        AccountAccess.PendingApproval -> {
                            Text(
                                text = "Waiting for administrator approval",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Your Google account is signed in, but it cannot access hostel data until an administrator approves it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(onClick = onRetry) { Text("Check approval") }
                                TextButton(onClick = onSignOut) { Text("Sign out") }
                            }
                        }
                        AccountAccess.Revoked -> {
                            Text(
                                text = "Access revoked",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "An administrator has disabled this account. Hostel data is unavailable.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onSignOut) { Text("Sign out") }
                        }
                        is AccountAccess.Approved -> {
                            Text(
                                text = "Access approved",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Role: ${targetAccess.account.role} · Hostel: ${targetAccess.account.hostelId}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val enterInteractionSource = rememberMotionInteractionSource()
                            Button(
                                onClick = onEnterApp,
                                interactionSource = enterInteractionSource,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .pressFeedback(enterInteractionSource),
                            ) {
                                Text("Enter Attendance App", fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = onSignOut) { Text("Sign out") }
                        }
                        is AccountAccess.Error -> {
                            Text(
                                text = "Account access could not be verified",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = targetAccess.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(onClick = onRetry) { Text("Retry") }
                                TextButton(onClick = onSignOut) { Text("Sign out") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalGlobalGlassBackdrop.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .liquidGlassEffect(
                backdrop = backdrop,
                density = density,
                cornerRadius = cornerRadius,
                blurRadius = 22.dp,
                refractionHeightPx = 22f,
                refractionAmountPx = 32f,
                surfaceColor = Color(0x4C161324),
            )
            .padding(contentPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
