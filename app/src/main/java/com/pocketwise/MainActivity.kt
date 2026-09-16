package com.pocketwise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.components.BottomNavBar
import com.pocketwise.core.ui.components.BottomNavDestinations
import com.pocketwise.core.ui.components.MonthSwitcher
import com.pocketwise.core.ui.theme.PocketWiseTheme
import com.pocketwise.feature.expense_core.AddExpenseScreen
import com.pocketwise.feature.expense_core.CategoryScreen
import com.pocketwise.feature.expense_core.CurrencySelector
import com.pocketwise.feature.expense_core.ExpenseViewModel
import com.pocketwise.feature.expense_core.HomeScreen
import com.pocketwise.feature.expense_core.ReportScreen
import com.pocketwise.feature.recurring.RecurringScreen
import com.pocketwise.feature.voice.VoiceExpenseViewModel
import com.pocketwise.feature.voice.VoiceReviewSheet
import com.pocketwise.feature.widget.EXTRA_EDIT_EXPENSE_ID
import com.pocketwise.feature.widget.EXTRA_OPEN_ADD_EXPENSE
import com.pocketwise.feature.widget.EXTRA_START_VOICE
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

private const val TABS_ROUTE = "tabs"
private const val KEY_LOCKED = "locked"
private const val LOCK_AFTER_MS = 60_000L
private const val LOCK_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL // BIOMETRIC_STRONG|DEVICE_CREDENTIAL isn't supported on API 28-29

// FragmentActivity (not ComponentActivity): BiometricPrompt needs one to host its dialog.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var userPreferences: UserPreferences

    // App lock: `locked` drives the UI; `lockEnabled` mirrors the setting for onStart().
    private var locked by mutableStateOf(false)
    private var lockEnabled = false
    private var backgroundedAt = 0L
    private lateinit var biometricPrompt: BiometricPrompt
    private var onAuthSucceeded: () -> Unit = {}

    // Widget actions (quick add / voice / edit) must run exactly once. Unlocking
    // rebuilds the UI, and a rotation re-delivers the same intent — neither
    // should reopen the form or restart listening.
    private var pendingWidgetAction = false

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        pendingWidgetAction = savedInstanceState == null

        // ponytail: one tiny DataStore read on the main thread, so a locked app never flashes its content.
        lockEnabled = runBlocking { userPreferences.appLockEnabled.first() }
        locked = savedInstanceState?.getBoolean(KEY_LOCKED) ?: lockEnabled
        lifecycleScope.launch {
            userPreferences.appLockEnabled.collect { enabled ->
                lockEnabled = enabled
                // Keep expenses out of the Recents preview without blocking the user's own screenshots.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!enabled)
            }
        }
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onAuthSucceeded()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // The phone no longer has any screen lock, so nobody can be
                    // verified — never lock the owner out of their own data.
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                        onAuthSucceeded()
                    }
                }
            }
        )

        setContent {
            PocketWiseTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (locked) {
                        LockScreen(onUnlock = { unlock() })
                        LaunchedEffect(Unit) { unlock() }
                        return@Surface
                    }

                    val navController = rememberNavController()
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    // The top-level tabs live in one swipeable pager rather than
                    // as separate nav destinations, so a horizontal swipe moves
                    // between them and the bottom bar just follows the page.
                    val pagerState = rememberPagerState(pageCount = { BottomNavDestinations.size })
                    val scope = rememberCoroutineScope()
                    // One month selection for the whole app: Activity-scoped and
                    // handed to every screen that shows month data, so they can
                    // never drift apart.
                    val expenseViewModel: ExpenseViewModel = hiltViewModel()
                    val appLockOn by userPreferences.appLockEnabled.collectAsState(initial = lockEnabled)

                    // Voice logging: the system speech UI does the listening (no
                    // mic permission needed), Gemini parses, and the review sheet
                    // shows the result — nothing is saved until the user confirms.
                    val voiceViewModel: VoiceExpenseViewModel = hiltViewModel()
                    // Gemini hears the recording itself, so any language (or a mix)
                    // works — the old on-device recognizer was locked to one language.
                    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            voiceViewModel.startListening()
                        } else {
                            voiceViewModel.onSpeechError("PocketWise needs microphone access to log expenses by voice. You can allow it in Settings → Apps → PocketWise.")
                        }
                    }
                    fun startVoice() {
                        voiceViewModel.warmUp()
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            voiceViewModel.startListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    // Never keep the mic open once the app is out of sight.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) voiceViewModel.stopListeningIfActive()
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    val voiceState by voiceViewModel.state.collectAsState()
                    if (voiceState !is VoiceExpenseViewModel.UiState.Idle) {
                        val voiceCategories by voiceViewModel.categories.collectAsState()
                        val voiceCurrency by voiceViewModel.currencyCode.collectAsState()
                        VoiceReviewSheet(
                            state = voiceState,
                            categories = voiceCategories,
                            currencySymbol = currencySymbolFor(voiceCurrency),
                            onSave = voiceViewModel::save,
                            onStopListening = voiceViewModel::stopListening,
                            onRetry = {
                                voiceViewModel.dismiss()
                                startVoice()
                            },
                            onEnterManually = {
                                voiceViewModel.dismiss()
                                navController.navigate("add_expense")
                            },
                            onDismiss = voiceViewModel::dismiss
                        )
                    }
                    // Widget shortcuts: mic starts listening, "+ Add" opens the form,
                    // a recent expense opens it for editing. Runs after unlock.
                    LaunchedEffect(Unit) {
                        if (!pendingWidgetAction) return@LaunchedEffect
                        pendingWidgetAction = false
                        val editId = intent.getLongExtra(EXTRA_EDIT_EXPENSE_ID, -1L)
                        when {
                            intent.getBooleanExtra(EXTRA_START_VOICE, false) -> startVoice()
                            editId != -1L -> navController.navigate("add_expense?expenseId=$editId")
                            intent.getBooleanExtra(EXTRA_OPEN_ADD_EXPENSE, false) -> navController.navigate("add_expense")
                        }
                    }
                    // Tabs + FAB only make sense on the top-level screens — not
                    // while inside the add/edit-expense flow, which is a
                    // modal-style task, not a destination you sit on.
                    val showChrome = currentRoute == TABS_ROUTE

                    // The FAB sits over the amount column of the list, so it gets out of the way:
                    // hidden while scrolling down, back on any scroll up. Listens on the pager, so
                    // every tab's list drives it. The guards keep writes to real changes only.
                    var fabVisible by remember { mutableStateOf(true) }
                    val fabScrollConnection = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (available.y < -1f && fabVisible) fabVisible = false
                                else if (available.y > 1f && !fabVisible) fabVisible = true
                                return Offset.Zero
                            }
                        }
                    }
                    // A tab too short to scroll could otherwise leave it stuck hidden.
                    LaunchedEffect(pagerState.currentPage) { fabVisible = true }

                    // Deletes happen instantly; Undo puts the same rows back.
                    // Lives on this outer Scaffold so it survives leaving the edit screen.
                    val snackbarHostState = remember { SnackbarHostState() }
                    LaunchedEffect(Unit) {
                        // collectLatest: a new delete replaces the showing snackbar.
                        expenseViewModel.deleted.collectLatest { deleted ->
                            val result = snackbarHostState.showSnackbar(
                                message = if (deleted.size == 1) "\"${deleted[0].description}\" deleted" else "${deleted.size} expenses deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) expenseViewModel.restoreExpenses(deleted)
                        }
                    }

                    Scaffold(
                        // Each screen has its own Scaffold + TopAppBar, which
                        // already reserves top status-bar space. Without this,
                        // this outer Scaffold reserves it *again*, doubling
                        // the gap above every screen's app bar.
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            // Snackbar lives here, not in Scaffold's snackbarHost slot:
                            // that slot always stacks above the FAB. Here it sits on
                            // the nav bar and the FAB rises above it instead.
                            Column {
                                SnackbarHost(
                                    snackbarHostState,
                                    // NavigationBar pads for the system bar itself; without it we must.
                                    modifier = if (showChrome) Modifier else Modifier.navigationBarsPadding()
                                )
                                // Animate the bar out instead of hard-hiding it —
                                // an instant pop right as the screen also cuts is
                                // what actually reads as janky, not just a missing
                                // screen transition on its own.
                                AnimatedVisibility(
                                    visible = showChrome,
                                    enter = fadeIn(tween(200)),
                                    exit = fadeOut(tween(120))
                                ) {
                                    BottomNavBar(
                                        currentRoute = BottomNavDestinations[pagerState.currentPage].route,
                                        onNavigate = { route ->
                                            val page = BottomNavDestinations.indexOfFirst { it.route == route }
                                            scope.launch { pagerState.animateScrollToPage(page) }
                                        }
                                    )
                                }
                            }
                        },
                        floatingActionButton = {
                            AnimatedVisibility(
                                visible = showChrome && fabVisible,
                                enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                                exit = scaleOut(tween(120)) + fadeOut(tween(120))
                            ) {
                                // One capsule instead of two separate FABs:
                                // mic in a squircle nested in the top half, +
                                // centered in the bottom half.
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shadowElevation = 6.dp
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable(onClick = { startVoice() })
                                        ) {
                                            Icon(
                                                Icons.Filled.Mic,
                                                contentDescription = "Log expense by voice",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .clickable(onClick = { navController.navigate("add_expense") })
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = "Add expense")
                                        }
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = TABS_ROUTE,
                            modifier = Modifier.padding(padding),
                            enterTransition = { fadeIn(tween(220)) },
                            exitTransition = { fadeOut(tween(220)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { fadeOut(tween(220)) }
                        ) {
                            composable(TABS_ROUTE) {
                                // Back from Reports/Categories returns to Home
                                // first, then leaves the app — same as before.
                                BackHandler(enabled = pagerState.currentPage != 0) {
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                }
                                val selectedMonth by expenseViewModel.selectedMonth.collectAsState()
                                val currencyCode by expenseViewModel.currencyCode.collectAsState()
                                val tab = BottomNavDestinations[pagerState.currentPage]

                                Column {
                                    // One header for every tab; the title follows the page.
                                    TopAppBar(
                                        title = {
                                            AnimatedContent(
                                                targetState = if (tab.route == "home") "PocketWise" else tab.label,
                                                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                                label = "tabTitle"
                                            ) { label ->
                                                if (label == "PocketWise") {
                                                    Text(
                                                        text = buildAnnotatedString {
                                                            append("Pocket")
                                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Wise") }
                                                        },
                                                        style = MaterialTheme.typography.titleLarge
                                                    )
                                                } else {
                                                    Text(label)
                                                }
                                            }
                                        },
                                        actions = {
                                            CurrencySelector(currencyCode = currencyCode, onSelect = expenseViewModel::setCurrency)
                                            OverflowMenu(
                                                appLockEnabled = appLockOn,
                                                onRecurring = { navController.navigate("recurring") },
                                                onToggleAppLock = { enable -> setAppLock(enable) }
                                            )
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                                    )

                                    // The month strip sits ABOVE the tab pager, not
                                    // inside it: swiping the strip changes month,
                                    // swiping the page below changes tab — two
                                    // separate touch areas, so the gestures can never
                                    // fight. Hidden on Categories (no month data),
                                    // keyed on the settled page so the layout doesn't
                                    // resize mid-swipe.
                                    AnimatedVisibility(visible = BottomNavDestinations[pagerState.settledPage].route != "categories") {
                                        MonthSwitcher(
                                            selectedMonth = selectedMonth,
                                            onMonthSelected = expenseViewModel::selectMonth,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }

                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier
                                            .weight(1f)
                                            .nestedScroll(fabScrollConnection),
                                        // Keep the neighbouring tab composed so a swipe
                                        // reveals a ready page instead of building it mid-drag.
                                        beyondViewportPageCount = 1,
                                        key = { BottomNavDestinations[it].route }
                                    ) { page ->
                                        when (BottomNavDestinations[page].route) {
                                            "home" -> HomeScreen(
                                                viewModel = expenseViewModel,
                                                onEditExpense = { id -> navController.navigate("add_expense?expenseId=$id") }
                                            )
                                            "reports" -> ReportScreen(viewModel = expenseViewModel)
                                            else -> CategoryScreen()
                                        }
                                    }
                                }
                            }
                            composable(
                                "recurring",
                                enterTransition = {
                                    slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(300)) +
                                        fadeIn(tween(300))
                                },
                                popExitTransition = {
                                    slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = tween(250)) +
                                        fadeOut(tween(200))
                                }
                            ) {
                                RecurringScreen(onBack = { navController.popBackStack() })
                            }
                            composable(
                                "add_expense?expenseId={expenseId}",
                                arguments = listOf(navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L }),
                                // Add/Edit Expense is a task pushed on top, not
                                // a peer tab — it should feel like a sheet
                                // rising into place, and sink back down when
                                // dismissed, not swap like a tab change.
                                enterTransition = {
                                    slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(300)) +
                                        fadeIn(tween(300))
                                },
                                popExitTransition = {
                                    slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = tween(250)) +
                                        fadeOut(tween(200))
                                }
                            ) { backStackEntry ->
                                val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: -1L
                                AddExpenseScreen(
                                    onDone = { navController.popBackStack() },
                                    expenseId = if (expenseId == -1L) null else expenseId,
                                    // Shared instance, so its delete events reach the snackbar above.
                                    expenseViewModel = expenseViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Relock only after a real absence — rotation, the speech screen, or the
        // PIN screen are short and shouldn't nag.
        if (lockEnabled && backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt >= LOCK_AFTER_MS) {
            locked = true
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
    }

    private fun unlock() {
        authenticate("Unlock PocketWise") { locked = false }
    }

    private fun setAppLock(enable: Boolean) {
        if (!enable) {
            lifecycleScope.launch { userPreferences.setAppLockEnabled(false) }
            return
        }
        if (BiometricManager.from(this).canAuthenticate(LOCK_AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Set a screen lock in your phone settings first", Toast.LENGTH_LONG).show()
            return
        }
        // Prove the lock works before saving it, so nobody can lock themselves out.
        authenticate("Turn on app lock") { lifecycleScope.launch { userPreferences.setAppLockEnabled(true) } }
    }

    private fun authenticate(title: String, onSuccess: () -> Unit) {
        onAuthSucceeded = onSuccess
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use your fingerprint, face, or screen lock")
                .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                .build()
        )
    }

    // Some OEM skins cap third-party apps below the display's max refresh
    // rate unless the window explicitly requests a faster mode. Filtering to
    // modes matching the CURRENT resolution before picking the fastest one
    // matters: some devices list separate Display.Mode entries pairing
    // different resolutions with different refresh rates, and grabbing the
    // single highest-Hz entry regardless of resolution can land on a
    // mismatched mode — which is what caused the erratic 10/30/120Hz
    // hunting last time, not the high refresh rate itself.
    private fun requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return

        val currentMode = display.mode
        val fastestMode = display.supportedModes
            .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return

        window.attributes = window.attributes.apply { preferredDisplayModeId = fastestMode.modeId }
    }
}

@Composable
private fun OverflowMenu(appLockEnabled: Boolean, onRecurring: () -> Unit, onToggleAppLock: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Recurring expenses") },
                leadingIcon = { Icon(Icons.Filled.Autorenew, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRecurring()
                }
            )
            DropdownMenuItem(
                text = { Text("App lock") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon = { Switch(checked = appLockEnabled, onCheckedChange = null) },
                onClick = {
                    expanded = false
                    onToggleAppLock(!appLockEnabled)
                }
            )
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Text(
            "PocketWise is locked",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Unlock with your fingerprint, face, or screen lock.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = onUnlock,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .padding(top = 28.dp)
                .height(52.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unlock")
        }
    }
}
