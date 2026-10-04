

package com.shiny.music
import com.shiny.music.R
import com.shiny.music.BuildConfig
import com.shiny.music.ui.component.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.shiny.music.constants.OnboardingCompletedKey
import com.shiny.music.constants.UseFloatingNavBarKey
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import android.Manifest
import android.annotation.SuppressLint
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import android.app.PendingIntent
import android.content.ComponentName
import android.animation.ValueAnimator
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.shiny.music.constants.AppBarHeight
import com.shiny.music.constants.AppLanguageKey
import com.shiny.music.constants.DarkModeKey
import com.shiny.music.constants.DefaultOpenTabKey
import com.shiny.music.constants.DisableScreenshotKey
import com.shiny.music.constants.DynamicThemeKey
import com.shiny.music.constants.EnableHighRefreshRateKey
import com.shiny.music.constants.FloatingToolbarBottomPadding
import com.shiny.music.constants.FloatingToolbarHorizontalPadding
import com.shiny.music.constants.ListenTogetherInTopBarKey
import com.shiny.music.constants.MiniPlayerBottomSpacing
import com.shiny.music.constants.MiniPlayerHeight
import com.shiny.music.constants.NavigationBarAnimationSpec
import com.shiny.music.constants.NavigationBarHeight
import com.shiny.music.shinymusic.updater.checkForUpdate
import com.shiny.music.shinymusic.updater.getAutoUpdateCheckSetting
import com.shiny.music.shinymusic.updater.isNewerVersion
import com.shiny.music.shinymusic.updater.saveUpdateAvailableState
import com.shiny.music.shinymusic.updater.getUpdateNotificationsSetting
import com.shiny.music.shinymusic.UpdateNotificationHelper
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import com.shiny.music.constants.PauseListenHistoryKey
import com.shiny.music.constants.PauseSearchHistoryKey
import com.shiny.music.constants.PureBlackKey
import com.shiny.music.constants.SYSTEM_DEFAULT
import com.shiny.music.constants.SelectedThemeColorKey
import com.shiny.music.constants.StopMusicOnTaskClearKey
import com.shiny.music.constants.UseNewMiniPlayerDesignKey
import com.shiny.music.constants.*
import com.shiny.music.ui.component.shimmer.getShimmerTheme
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.SearchHistory
import com.shiny.music.extensions.toEnum
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.playback.DownloadUtil
import com.shiny.music.playback.MusicService
import com.shiny.music.playback.MusicService.MusicBinder
import com.shiny.music.playback.PlayerConnection
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.ui.component.*
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.menu.YouTubeSongMenu
import com.shiny.music.ui.liquid.LocalLiquidBottomInset
import com.shiny.music.ui.liquid.LocalPageCovered
import com.shiny.music.ui.liquid.player.LiquidPlayerSheet
import com.shiny.music.ui.liquid.appearance.ArtworkAccent
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.shell.LiquidNav
import com.shiny.music.ui.liquid.shell.isTabSwitch
import com.shiny.music.ui.liquid.shell.LiquidChromeBottomMargin
import com.shiny.music.ui.liquid.shell.LiquidTab
import com.shiny.music.ui.liquid.shell.LiquidTabBar
import com.shiny.music.ui.liquid.shell.LiquidRail
import com.shiny.music.ui.liquid.shell.chromeReservedHeight
import com.shiny.music.ui.liquid.shell.liquidTabIcon
import com.shiny.music.ui.liquid.shell.rememberLiquidChromeState
import com.shiny.music.ui.player.LocalPlayerExpansion
import com.shiny.music.ui.player.PlayerArtworkFlight
import com.shiny.music.ui.player.rememberPlayerExpansionState
import com.shiny.music.ui.screens.Screens
import com.shiny.music.ui.screens.navigationBuilder
import com.shiny.music.ui.screens.settings.DarkMode
import com.shiny.music.ui.screens.settings.NavigationTab
import com.shiny.music.ui.theme.ColorSaver
import com.shiny.music.ui.theme.DefaultThemeColor
import com.shiny.music.ui.theme.ShinyTheme
import com.shiny.music.ui.utils.appBarScrollBehavior
import com.shiny.music.ui.utils.resetHeightOffset
import com.shiny.music.utils.SyncUtils
import com.shiny.music.utils.PreferencesSnapshot
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import com.shiny.music.utils.reportException
import com.shiny.music.utils.setAppLocale
import com.shiny.music.viewmodels.HomeViewModel
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import com.shiny.music.utils.getOrNull

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        /** Once per process: the library check is not worth repeating on recreation. */
        @Volatile
        private var localLibraryChecked = false
        private const val LOCAL_LIBRARY_CHECK_DELAY_MS = 10_000L

        const val ACTION_SEARCH = "com.shiny.music.action.SEARCH"
        const val ACTION_LIBRARY = "com.shiny.music.action.LIBRARY"
        const val ACTION_RECOGNITION = "com.shiny.music.action.RECOGNITION"
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"

        /**
         * Upper bound on how long the launch waits for the preference snapshot before
         * giving up and composing against defaults. A DataStore that never emits should
         * cost the user a slower launch, not a permanently blank screen.
         */
        private const val SPLASH_MAX_HOLD_MS = 1000L

        /**
         * Splash cross-fade. Long enough to blend the hand-over, short enough that it
         * reads as the app appearing rather than as a transition being performed.
         */
        private const val SPLASH_EXIT_FADE_MS = 180L

        /** The splash's fade when the launch artwork is underneath it. */
        private const val SPLASH_TO_ARTWORK_FADE_MS = 280L

        /**
         * Longest the splash waits, once the app is ready, for the artwork's decode (tens of
         * milliseconds on a phone). A slower one skips the artwork rather than hold the app.
         */
        private const val ARTWORK_DECODE_MAX_WAIT_MS = 250L

        /**
         * Latest the first service bind waits for an idle main thread. Short, because a tap
         * on a song before the service is connected has no player to go to.
         */
        private const val FIRST_BIND_MAX_DELAY_MS = 500L
    }

    @Inject
    lateinit var database: MusicDatabase

    /**
     * Lazy, and started on a background thread from [onCreate]. Its constructor opens both
     * media caches, builds the DownloadManager and reads the whole download index from SQLite;
     * as a plain injected field all of that ran on the main thread ahead of the first frame.
     */
    @Inject
    lateinit var downloadUtil: dagger.Lazy<DownloadUtil>

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var together: com.shiny.music.together.TogetherSession

    /** Lazy: only the delayed library check below uses it. */
    @Inject
    lateinit var localSongScanner: dagger.Lazy<com.shiny.music.localmedia.LocalSongScanner>
    private var pendingIntent: Intent? = null

    /**
     * Whether a composed handler is currently attached via [addOnNewIntentListener]. While
     * one is, [onNewIntent] has nothing to do beyond `super`, which dispatches to it.
     */
    private var newIntentListenerRegistered = false

    /** When the launch began, for the deadline that bounds the splash hold. */
    private var launchStartedAt = 0L

    /**
     * Set from a [SideEffect] once a composition carrying the resolved preferences has
     * been applied, which happens before that frame is drawn. Main thread only, so a
     * plain field is enough — the pre-draw listener that reads it runs there too.
     */
    private var contentReadyForDisplay = false

    /** When [contentReadyForDisplay] became true, for the bound on the artwork's decode. */
    private var contentReadyAt = 0L

    /** Guards the splash hand-over, which is armed from two places. Main thread only. */
    private var splashHandedOver = false

    /** The launch artwork is up: a cold launch from the launcher, with animations on. */
    private var launchArtVisible by mutableStateOf(false)

    /** The system splash has begun to leave, which is when the launch artwork is composed. */
    private var splashLeaving by mutableStateOf(false)

    /**
     * The artwork hides the app completely (it is opaque and not yet fading), so the app is
     * not drawn beneath it: every frame would otherwise paint both. It still composes and
     * loads as usual, and is drawn again the moment the artwork starts to fade.
     */
    private var launchArtCoversApp by mutableStateOf(false)

    /** The bar appearance the app last asked for, held back while the artwork owns the bars. */
    private var requestedDarkBars: Boolean? = null

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                try {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    Timber.tag("MainActivity").d("PlayerConnection created successfully")
                    together.attach(playerConnection)
                } catch (e: Exception) {
                    Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection")
                    
                    lifecycleScope.launch {
                        delay(500)
                        try {
                            playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                            together.attach(playerConnection)
                        } catch (e2: Exception) {
                            Timber.tag("MainActivity").e(e2, "Failed to create PlayerConnection on retry")
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            together.attach(null)
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onStart() {
        super.onStart()
        // Back after a while with nothing playing: the Equaliser goes back to off.
        if (!isChangingConfigurations) com.shiny.music.eq.fx.SoundFxEngine.onAppOpened(quietMs = 120_000L)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        
        
        
        if (hasStartedBefore) {
            bindPlaybackService(requireStarted = false)
        } else {
            // First start only. Binding creates the service on this thread, and its onCreate —
            // ExoPlayer, the media session — used to run inside the launch's first frame.
            // It now waits for the first idle moment after that frame, with a short timer as a
            // backstop in case the queue stays busy.
            android.os.Looper.myQueue().addIdleHandler {
                bindPlaybackService(requireStarted = true)
                false
            }
            window.decorView.postDelayed({ bindPlaybackService(requireStarted = true) }, FIRST_BIND_MAX_DELAY_MS)
        }
        hasStartedBefore = true
    }

    /** Whether [serviceConnection] is bound. Main thread only. */
    private var serviceBound = false

    /** Whether this activity has been started before; every start after the first binds at once. */
    private var hasStartedBefore = false

    private fun bindPlaybackService(requireStarted: Boolean) {
        if (serviceBound) return
        // A deferred bind can land after the activity has already been stopped again.
        if (requireStarted && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
        serviceBound = true
    }

    override fun onStop() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Recreated (rotation, theme) while the artwork was up: the new instance never shows it.
        if (launchArtVisible) {
            launchArtVisible = false
            com.shiny.music.ui.liquid.launch.LaunchArt.release()
        }
        if (dataStore.get(StopMusicOnTaskClearKey, false) &&
            playerConnection?.isPlaying?.value == true &&
            isFinishing
        ) {
            stopService(Intent(this, MusicService::class.java))
            if (serviceBound) {
                unbindService(serviceConnection)
                serviceBound = false
            }
            playerConnection = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        // Dispatches to the listener the composition registers below, which is where the
        // live handlers are. This used to branch on a `lateinit navController` field that
        // nothing ever assigned — the composable declares a local of the same name that
        // shadows it — so the branch was always false and every warm-launch intent was
        // filed as pending instead, to be replayed at some arbitrary later composition.
        super.onNewIntent(intent)
        // Without this, getIntent() keeps returning the intent the Activity was launched
        // with, and the launch handler below reads getIntent().
        setIntent(intent)
        // Only for the window between this Activity being created and its first
        // composition, where there is nothing composed to receive the intent yet.
        if (!newIntentListenerRegistered) {
            pendingIntent = intent
        }
    }

    private var isPlaying = false

    override fun startForegroundService(service: Intent): android.content.ComponentName? {
        return try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Timber.e(e, "Suppressed ForegroundServiceStartNotAllowedException in MainActivity")
                null
            } else {
                throw e
            }
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, because the post-splash theme has to be applied before
        // the window is created.
        val splashScreen = installSplashScreen()
        // Opened afresh (not a rotation or theme change): an Equaliser left on last time goes
        // back to off, unless a song is playing through it right now.
        if (savedInstanceState == null) com.shiny.music.eq.fx.SoundFxEngine.onAppOpened()

        // Idempotent, and normally already running from App.onCreate; repeated here so
        // the gate below can never wait on a collector that was never started. No scope
        // is passed on purpose — the snapshot must outlive this Activity.
        PreferencesSnapshot.start(this)
        // The first screen's covers, decoded while the activity starts (once per process).
        com.shiny.music.ui.liquid.CoverWarmup.start(this, (application as App).applicationScope)

        launchStartedAt = SystemClock.uptimeMillis()
        // Only a fresh launch from the launcher shows the first-screen artwork: a shared link, a
        // shortcut or a notification goes straight to what it opens, and "Remove animations"
        // is honoured. Nothing of it runs here — see the keep-on-screen condition below.
        launchArtVisible = savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN &&
            intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true &&
            ValueAnimator.areAnimatorsEnabled()
        // The splash is held until a composed frame carrying the real preferences has
        // been applied — not merely until the preferences arrived. Those are different
        // moments: the snapshot lands on the main thread, but the composition that reads
        // it runs on the next frame, so releasing on the snapshot alone can reveal one
        // frame of default-themed content before the corrected one draws.
        //
        // The first-screen artwork costs the start-up nothing: it is decoded only once that
        // frame is ready, so it never competes with the app's own work, and the splash covers
        // the few milliseconds the decode takes. That time comes out of nothing the app does:
        // the app is ready underneath, and the artwork's time on screen starts after it.
        splashScreen.setKeepOnScreenCondition {
            val now = SystemClock.uptimeMillis()
            if (!contentReadyForDisplay) {
                now - launchStartedAt < SPLASH_MAX_HOLD_MS
            } else {
                launchArtVisible &&
                    com.shiny.music.ui.liquid.launch.LaunchArt.image.value == null &&
                    now - contentReadyAt < ARTWORK_DECODE_MAX_WAIT_MS
            }
        }
        // A plain cross-fade, and deliberately nothing else. The window underneath is
        // already painted the colour the content draws, so in the ordinary case there is
        // nothing to see; the fade exists to soften the hand-over in the cases where the
        // two cannot match exactly — an appearance preference that overrides the system
        // configuration the launch theme was chosen from, or pure black.
        splashScreen.setOnExitAnimationListener { splashProvider ->
            if (launchArtVisible && com.shiny.music.ui.liquid.launch.LaunchArt.image.value == null) {
                finishLaunchArt()
            }
            splashLeaving = true
            // Into the artwork, a slightly longer blend: the empty splash is the artwork's sky
            // colour, so the artwork seems to appear out of it rather than replace it.
            val fadeMs = if (launchArtVisible) SPLASH_TO_ARTWORK_FADE_MS else SPLASH_EXIT_FADE_MS
            // `remove()` is what actually hands the window over, and ViewPropertyAnimator
            // skips `withEndAction` when an animation is cancelled rather than finished.
            // A fade cancelled by the view being detached mid-launch would therefore
            // leave the splash sitting on top of a live, interactive app. The hand-over
            // is made idempotent and also armed on a timer, so it happens whether or not
            // the animation reaches its end.
            val handOver = Runnable {
                if (!splashHandedOver) {
                    splashHandedOver = true
                    splashProvider.remove()
                }
            }
            splashProvider.view
                .animate()
                .alpha(0f)
                .setDuration(fadeMs)
                .setInterpolator(LinearInterpolator())
                .withEndAction(handOver)
                .start()
            splashProvider.view.postDelayed(handOver, fadeMs * 2)
        }

        super.onCreate(savedInstanceState)
        // Builds DownloadUtil (see the field) while the rest of the launch proceeds; the first
        // composition's get() then waits only for whatever is left of it, if anything.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { downloadUtil.get() }
        scheduleLocalLibraryCheck()
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Below Tiramisu there is no per-app locale, so the configuration has to be
            // updated by hand — and it has to happen before anything resolves a string,
            // which rules out waiting for the snapshot asynchronously: there is no later
            // moment to correct it that does not show the wrong language first.
            //
            // The snapshot is the normal source. App.onCreate starts the collector well
            // before this runs, so on a cold launch it has almost always landed by now
            // and nothing blocks. The blocking read is kept only as the fallback for the
            // case where it has not, which is the same trade the code made unconditionally
            // before.
            val languageTag =
                if (PreferencesSnapshot.isLoaded) {
                    PreferencesSnapshot.readString(AppLanguageKey)
                } else {
                    dataStore[AppLanguageKey]
                }
            val locale = languageTag
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        if (java.io.File(filesDir, "clear_export_state").exists()) {
            lifecycleScope.launch {
                dataStore.edit { preferences ->
                    preferences.remove(com.shiny.music.constants.ExportingSongIdsKey)
                    preferences.remove(com.shiny.music.constants.ExportedSongIdsKey)
                    preferences.remove(com.shiny.music.constants.ExportProgressKey)
                }
                java.io.File(filesDir, "clear_export_state").delete()
            }
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it.getOrNull(DisableScreenshotKey) ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }
        
        setContent {
            Box(Modifier.fillMaxSize(), propagateMinConstraints = true) {
                // Nothing composes until the preference snapshot holds a value.
                //
                // Several things inside ShinyApp read a preference exactly once and keep the
                // answer for the life of the process — the NavHost start destination is
                // captured when the graph is built, and the theme colour is seeded into a
                // rememberSaveable. Composing before the snapshot lands would latch a
                // default that no later emission can undo, so a user whose default tab is
                // Library would still open on Home.
                //
                // Waiting costs nothing visible: the splash is covering the window, and in
                // the ordinary case the snapshot is already loaded by the time the activity
                // gets here because App.onCreate started the collector well before this.
                // The same deadline that releases the splash releases this, so a DataStore
                // that never emits degrades to defaults rather than to a blank screen.
                val preferencesReady = PreferencesSnapshot.hasValue
                var deadlineElapsed by remember { mutableStateOf(false) }

                if (!preferencesReady && !deadlineElapsed) {
                    LaunchedEffect(Unit) {
                        val remaining =
                            SPLASH_MAX_HOLD_MS - (SystemClock.uptimeMillis() - launchStartedAt)
                        if (remaining > 0) delay(remaining)
                        deadlineElapsed = true
                    }
                }

                if (preferencesReady || deadlineElapsed) {
                    Box(
                        modifier = if (launchArtVisible) {
                            Modifier.graphicsLayer { alpha = if (launchArtCoversApp) 0f else 1f }
                        } else {
                            Modifier
                        },
                        propagateMinConstraints = true,
                    ) {
                        ShinyApp(
                            playerConnection = playerConnection,
                            database = database,
                            downloadUtil = downloadUtil.get(),
                            syncUtils = syncUtils,
                        )
                    }

                    // Runs while this composition's changes are being applied, which is
                    // before the frame it belongs to is measured and drawn — so by the time
                    // the pre-draw listener above checks, the content behind the splash is
                    // the finished article rather than one that still has a pass to go.
                    SideEffect {
                        if (!contentReadyForDisplay) {
                            contentReadyForDisplay = true
                            contentReadyAt = SystemClock.uptimeMillis()
                            if (launchArtVisible) com.shiny.music.ui.liquid.launch.LaunchArt.load(this@MainActivity)
                        }
                    }
                }

                // Above the app, which composes and loads underneath while it plays. Not before
                // the splash starts to leave: until then the app's first frame has the main
                // thread to itself.
                if (launchArtVisible && splashLeaving) {
                    val art by com.shiny.music.ui.liquid.launch.LaunchArt.image.collectAsState()
                    art?.let { image ->
                        com.shiny.music.ui.liquid.launch.LaunchArtwork(
                            image = image,
                            onCoversApp = { launchArtCoversApp = it },
                            onFinished = ::finishLaunchArt,
                        )
                    }
                }
            }
        }
    }

    /** Takes the launch artwork down and gives its memory and the system bars back. */
    private fun finishLaunchArt() {
        if (!launchArtVisible) return
        launchArtCoversApp = false
        launchArtVisible = false
        com.shiny.music.ui.liquid.launch.LaunchArt.release()
        requestedDarkBars?.let(::setSystemBarAppearance)
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ShinyApp(
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = false)
        val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
        val context = LocalContext.current
        var showUpdateDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
        var availableUpdateVersion by remember { androidx.compose.runtime.mutableStateOf("") }
        var availableUpdateChangelog by remember { androidx.compose.runtime.mutableStateOf<List<com.shiny.music.shinymusic.updater.ChangelogSection>>(emptyList()) }
        var availableUpdateDescription by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val prefs = context.dataStore.data.first()

            if (getAutoUpdateCheckSetting(context) &&
                com.shiny.music.shinymusic.updater.claimStartupUpdateCheck(context)
            ) {
                
                delay(2000L)
                checkForUpdate(
                    context = context,
                    onSuccess = { latestVersion, isAvailable, changelog, _, _, description, _, _ ->
                        val currentVersion = BuildConfig.VERSION_NAME
                        Log.d("UpdateCheck", "Startup check success. Latest: $latestVersion, Current: $currentVersion, isAvailable: $isAvailable")
                        saveUpdateAvailableState(context, isAvailable)
                        
                        if (isAvailable) {
                            availableUpdateVersion = latestVersion
                            availableUpdateChangelog = changelog
                            availableUpdateDescription = description
                            showUpdateDialog = true
                        }

                        if (isAvailable && getUpdateNotificationsSetting(context)) {
                            Log.d("UpdateCheck", "Posting update notification for $latestVersion")
                            UpdateNotificationHelper.showUpdateNotification(context, latestVersion)
                        }
                    },
                    onError = {
                        Log.e("UpdateCheck", "Startup check failed")
                        
                    }
                )
            }
        }

        LaunchedEffect(enableHighRefreshRate) {
            val window = this@MainActivity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val layoutParams = window.attributes
                if (enableHighRefreshRate) {
                    layoutParams.preferredDisplayModeId = 0
                } else {
                    val modes = window.windowManager.defaultDisplay.supportedModes
                    val mode60 = modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
                        ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

                    if (mode60 != null) {
                        layoutParams.preferredDisplayModeId = mode60.modeId
                    }
                }
                window.attributes = layoutParams
            } else {
                val params = window.attributes
                if (enableHighRefreshRate) {
                    params.preferredRefreshRate = 0f
                } else {
                    params.preferredRefreshRate = 60f
                }
                window.attributes = params
            }
        }

        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }

        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
        val selectedThemeColor = Color(selectedThemeColorInt)

        var themeColor by rememberSaveable(stateSaver = ColorSaver) {
            mutableStateOf(selectedThemeColor)
        }

        LaunchedEffect(selectedThemeColor) {
            if (!enableDynamicTheme) {
                themeColor = selectedThemeColor
            }
        }

        LaunchedEffect(playerConnection, enableDynamicTheme, selectedThemeColor) {
            val playerConnection = playerConnection
            if (!enableDynamicTheme || playerConnection == null) {
                themeColor = selectedThemeColor
                return@LaunchedEffect
            }

            // Once per artwork, not once per metadata emission (a like or a title fix
            // re-emits the same cover). Artwork with no usable colour, or that cannot be
            // read, falls back to Shiny Rose — never to a tint chosen before Artwork was.
            playerConnection.service.currentMediaMetadata
                .map { it?.thumbnailUrl }
                .distinctUntilChanged()
                .collectLatest { url ->
                    themeColor = url?.let { ArtworkAccent.of(this@MainActivity, it) } ?: DefaultThemeColor
                }
        }

        val (enableHaptics) = rememberPreference(com.shiny.music.constants.EnableHapticsKey, defaultValue = false)
        val view = LocalView.current
        var lastScrollHapticTime by remember { mutableStateOf(0L) }

        ShinyTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = themeColor,
        ) {

        // The window sits behind the Compose content and is what the splash fades onto.
        // Its colour comes from a resource qualifier, which can only follow the *system*
        // configuration — so when the appearance preferences disagree with it (dark mode
        // forced on a light system, or pure black), the launch theme guessed wrong.
        // Repainting the window with the colour the content actually draws settles that
        // at runtime, without a blocking read and without the resource system needing to
        // know about a preference it cannot see.
        // Confined to the hand-over. Once the splash is gone the Compose content covers
        // the window completely, so repainting it again would be invisible work — and
        // `surface` moves with the artwork palette, so an unguarded version would swap
        // the window background on every track change.
        val windowSurface = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
        SideEffect {
            if (!contentReadyForDisplay) {
                window.setBackgroundDrawable(ColorDrawable(windowSurface.toArgb()))
                // Status and navigation bar icons are otherwise driven by a
                // LaunchedEffect, whose body is dispatched to run *after* the composition
                // that scheduled it — a frame later than the content it has to contrast
                // against. That is enough for the first frame revealed by the splash to
                // carry dark icons over a dark surface. Setting it here puts it in the
                // same apply pass as that frame; the LaunchedEffect still owns every
                // later change.
                setSystemBarAppearance(useDarkTheme)
            }
        }

        if (showUpdateDialog) {
            com.shiny.music.ui.liquid.UpdateSheet(
                version = availableUpdateVersion,
                description = availableUpdateDescription,
                changelog = availableUpdateChangelog,
                onDismiss = { showUpdateDialog = false }
            )
        }
        // Export as MP3 outlives the menu that starts it, so its dialogs live here.
        com.shiny.music.export.AudioExportHost()
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                    .pointerInput(enableHaptics) {
                        if (enableHaptics) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val isClick = event.changes.any { it.changedToDown() }
                                    val isScroll = event.changes.any { it.positionChange() != Offset.Zero && it.pressed }
                                    if (isClick) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    } else if (isScroll) {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastScrollHapticTime > 100) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            lastScrollHapticTime = currentTime
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                val focusManager = LocalFocusManager.current
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val cutoutInsets = WindowInsets.displayCutout
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = hiltViewModel()
                val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
                val navigationItems = remember(listenTogetherInTopBar) { 
                    if (listenTogetherInTopBar) {
                        Screens.MainScreens.filter { it != Screens.ListenTogether }
                    } else {
                        Screens.MainScreens
                    }
                }
                val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                val defaultOpenTab = remember {
                    PreferencesSnapshot.readString(DefaultOpenTabKey)
                        .toEnum(defaultValue = NavigationTab.HOME)
                }
                val tabOpenedFromShortcut = remember {
                    when (intent?.action) {
                        ACTION_SEARCH -> NavigationTab.LIBRARY
                        ACTION_LIBRARY -> NavigationTab.SEARCH
                        else -> null
                    }
                }

                val topLevelScreens = remember {
                    listOf(
                        Screens.Home.route,
                        Screens.Library.route,
                        Screens.ListenTogether.route,
                        "settings",
                    )
                }

                val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue())
                }

                val onSearch: (String) -> Unit = remember {
                    { searchQuery ->
                        if (searchQuery.isNotEmpty()) {
                            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")

                            if (!PreferencesSnapshot.read(PauseSearchHistoryKey, false)) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    database.query {
                                        insert(SearchHistory(query = searchQuery))
                                    }
                                }
                            }
                        }
                    }
                }

                
                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }

                val inSearchScreen by remember {
                    derivedStateOf { currentRoute?.startsWith("search/") == true }
                }
                val navigationItemRoutes = remember(navigationItems) {
                    navigationItems.map { it.route }.toSet()
                }

                val shouldShowNavigationBar = remember(currentRoute, navigationItemRoutes) {
                    currentRoute == null ||
                        navigationItemRoutes.contains(currentRoute) ||
                        currentRoute!!.startsWith("search/") ||
                        currentRoute!!.startsWith("album/") ||
                        currentRoute!!.startsWith("online_playlist/") ||
                        currentRoute!!.startsWith("local_playlist/") ||
                        currentRoute!!.startsWith("artist/")
                }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

                val showRail = isLandscape && !inSearchScreen && currentRoute != "ambient_mode"

                val navPadding = if (shouldShowNavigationBar && !showRail) {
                    NavigationBarHeight + FloatingToolbarBottomPadding
                } else {
                    0.dp
                }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val (useFloatingNavBar) = rememberPreference(UseFloatingNavBarKey, defaultValue = true)
                val floatingNavBarScrollConnection = rememberFloatingTabBarScrollConnection()

                val playerBottomSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    // The mini player lives in the floating Liquid chrome on every screen, so
                    // the sheet itself has no visible collapsed state: it rises from nothing.
                    collapsedBound = 0.dp,
                    expandedBound = maxHeight,
                )

                // The mini player and the full player read their shared geometry from
                // here. It owns nothing and animates nothing — the sheet above is still
                // the only thing that moves — but it is the one place both ends of the
                // transition can agree on where the artwork is.
                val playerExpansionState = rememberPlayerExpansionState(playerBottomSheetState)

                val onShuffleClick: (() -> Unit)? = remember(playerConnection, playerBottomSheetState) {
                    playerConnection?.let { connection ->
                        {
                            if (playerBottomSheetState.isExpanded) {
                                playerBottomSheetState.collapseSoft()
                            }
                            connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
                        }
                    }
                }
                val shuffleEnabled by playerConnection?.shuffleModeEnabled?.collectAsState() ?: remember { mutableStateOf(false) }

                val onMusicRecognitionClick: (() -> Unit) = remember(navController, playerBottomSheetState) {
                    {
                        if (playerBottomSheetState.isExpanded) {
                            playerBottomSheetState.collapseSoft()
                        }
                        navController.navigate("recognition") {
                            launchSingleTop = true
                        }
                    }
                }

                val liveMediaMetadata by (playerConnection?.mediaMetadata?.collectAsState()
                    ?: remember { mutableStateOf(null) })
                val hasMedia = liveMediaMetadata != null
                val showLiquidTabs = shouldShowNavigationBar && !showRail
                val chromeHidden = currentRoute == "update" ||
                    currentRoute == "together/chat" ||
                    currentRoute == "login" ||
                    currentRoute == "ambient_mode" ||
                    currentRoute == "uptime" ||
                    currentRoute == "recognition"
                val showLiquidAccessory = hasMedia && !chromeHidden
                val liquidChromeState = rememberLiquidChromeState()

                // Everything floating at the bottom of the window: the system bar, the
                // tab bar and the mini player accessory. Pages end their content above it.
                val liquidBottomInset = bottomInset + LiquidChromeBottomMargin +
                    chromeReservedHeight(showLiquidTabs, showLiquidAccessory)

                val playerAwareWindowInsets = remember(liquidBottomInset) {
                    windowsInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        .add(WindowInsets(top = AppBarHeight, bottom = liquidBottomInset))
                }
                appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    }
                )

                val topAppBarScrollBehavior = appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                
                LaunchedEffect(navBackStackEntry) {
                    if (inSearchScreen) {
                        val searchQuery = withContext(Dispatchers.IO) {
                            val rawQuery = navBackStackEntry?.arguments?.getString("query")!!
                            try {
                                URLDecoder.decode(rawQuery, "UTF-8")
                            } catch (e: IllegalArgumentException) {
                                rawQuery
                            }
                        }
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length)
                            )
                        )
                    } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }

                    
                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    
                    navController.currentBackStackEntry?.destination?.route?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(playerConnection) {
                    val player = playerConnection?.player ?: return@LaunchedEffect
                    if (player.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                    } else {
                        if (playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }
                }

                DisposableEffect(playerConnection, playerBottomSheetState) {
                    val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                mediaItem != null &&
                                playerBottomSheetState.isDismissed
                            ) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry, listenTogetherInTopBar) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    val isListenTogetherScreen = currentRoute == Screens.ListenTogether.route ||
                        currentRoute == "together"
                    // Library draws its own header. The shared bar is a collapsing one
                    // — the NavHost feeds it a nested-scroll connection and it shrinks to
                    // nothing on the first flick — which is right for a feed and wrong for
                    // a screen whose category index has to stay reachable while you are
                    // half-way down a wall of covers.
                    shouldShowTopBar = currentRoute in topLevelScreens &&
                        currentRoute != "settings" &&
                        currentRoute != Screens.Library.route &&
                        !(isListenTogetherScreen && listenTogetherInTopBar)
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }

                // The service gives up on a song quietly otherwise: the music just stops, or
                // jumps to the next song with no reason given.
                LaunchedEffect(snackbarHostState) {
                    com.shiny.music.utils.PlaybackHealth.notices.collect { notice ->
                        val title = notice.title?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.playback_failed_untitled)
                        val message = getString(
                            when {
                                notice.unavailable && notice.skipped -> R.string.playback_unavailable_skipped
                                notice.unavailable -> R.string.playback_unavailable_stopped
                                notice.skipped -> R.string.playback_failed_skipped
                                else -> R.string.playback_failed_stopped
                            },
                            title,
                        )
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }

                val (lastOpenedVersionCode, setLastOpenedVersionCode) = rememberPreference(com.shiny.music.constants.LastOpenedVersionCodeKey, -1)
                val (onboardingCompleted, setOnboardingCompleted) = rememberPreference(OnboardingCompletedKey, false)
                var showWelcomeDialog by remember { mutableStateOf(false) }

                LaunchedEffect(lastOpenedVersionCode, onboardingCompleted) {
                    // Suppressed on a true first run: onboarding already covers the app, and
                    // stacking a "what's new" dialog on top of it would greet a brand new
                    // user with release notes for a version they have never seen.
                    if (onboardingCompleted && lastOpenedVersionCode < BuildConfig.VERSION_CODE) {
                        showWelcomeDialog = true
                    }
                }

                // Once per launch rather than once per composition. The launch intent
                // outlives a configuration change, and two of the three handlers do not
                // consume what they read — a voice search leaves SearchManager.QUERY in
                // place, and the recognition handler navigates on the action alone — so
                // without a saved marker, rotating the device or toggling the theme after
                // one of those launches would navigate back to the launch destination and
                // discard wherever the user had got to since. Saved rather than
                // remembered, so it also survives process death and restore.
                var launchIntentHandled by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (launchIntentHandled) return@LaunchedEffect
                    launchIntentHandled = true

                    val queued = pendingIntent
                    if (queued != null) {
                        pendingIntent = null
                        handleDeepLinkIntent(queued, navController)
                        handleRecognitionIntent(queued, navController)
                        handleAssistantSearchIntent(queued, navController)
                    } else if (intent != null && (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND)) {
                        handleDeepLinkIntent(intent, navController)
                    } else if (intent != null && intent.action == ACTION_RECOGNITION) {
                        handleRecognitionIntent(intent, navController)
                    } else if (intent != null && intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
                        handleAssistantSearchIntent(intent, navController)
                    }
                }

                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> { intent ->
                        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
                            handleDeepLinkIntent(intent, navController)
                        } else if (intent.action == ACTION_RECOGNITION) {
                            handleRecognitionIntent(intent, navController)
                        } else if (intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
                            handleAssistantSearchIntent(intent, navController)
                        }
                    }

                    addOnNewIntentListener(listener)
                    newIntentListenerRegistered = true
                    onDispose {
                        newIntentListenerRegistered = false
                        removeOnNewIntentListener(listener)
                    }
                }

                val accountName by homeViewModel.accountName.collectAsState()

                // Home's header greets rather than announcing the brand.
                //
                // "Shiny" was the one line of the opening viewport that could not tell a
                // listener anything they did not already know — they just tapped the icon.
                // A greeting costs the same 22sp and makes the first line contextual and
                // personal, which is the note Home should open on. It re-reads the clock
                // whenever the back stack changes, so returning to Home after an evening's
                // listening does not still say "Good afternoon".
                //
                // The other three tabs keep their plain titles: they are utility screens
                // and a greeting on a search results page would be noise.
                val isHomeRoute = navBackStackEntry?.destination?.route == Screens.Home.route
                val greeting = if (isHomeRoute) {
                    val hour = remember(navBackStackEntry) {
                        java.time.LocalTime.now().hour
                    }
                    val base = stringResource(
                        when (hour) {
                            in 5..11 -> R.string.greeting_morning
                            in 12..16 -> R.string.greeting_afternoon
                            in 17..21 -> R.string.greeting_evening
                            else -> R.string.greeting_night
                        }
                    )
                    // Only when there is a real name behind it. The signed-out default is
                    // the literal string "Guest", and "Good evening, Guest" is worse than
                    // no name at all.
                    val firstName = accountName.trim().substringBefore(' ')
                        .takeIf { it.isNotEmpty() && !it.equals("Guest", ignoreCase = true) }
                    if (firstName != null) {
                        stringResource(R.string.greeting_with_name, base, firstName)
                    } else {
                        base
                    }
                } else {
                    ""
                }

                // Home's bar is flush with the feed.
                //
                // The filled `surfaceContainer` band drew a hard seam across the top of the
                // page and made the greeting read as chrome bolted above the content rather
                // than the first line of it. Nothing is lost by removing it here: the bar is
                // only on screen at rest — the moment the feed moves it is gone and the
                // content runs to the status bar — so there is never a row passing behind a
                // same-coloured bar for the band to disambiguate.
                //
                // The utility tabs keep the band; they are not editorial pages.
                val homeAwareAppBarColor = when {
                    pureBlack -> Color.Black
                    isHomeRoute -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }

                val currentTitle = when (navBackStackEntry?.destination?.route) {
                    Screens.Home.route -> greeting
                    Screens.Search.route -> stringResource(R.string.search)
                    Screens.Library.route -> stringResource(R.string.filter_library)
                    Screens.ListenTogether.route -> stringResource(R.string.together)
                    else -> ""
                }



                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by database.eventCount().collectAsState(initial = 0)
                val showHistoryButton = remember(pauseListenHistory, eventCount) {
                    !(pauseListenHistory && eventCount == 0)
                }

                // Only the master switch reaches the Liquid glass; the old per-component and
                // tuning preferences had no live reader, so they are no longer collected here.
                val (liquidGlassGlobalEnabled) = rememberPreference(LiquidGlassGlobalEnabledKey, defaultValue = true)
                val glassEffectConfig = remember(liquidGlassGlobalEnabled, useFloatingNavBar) {
                    GlassEffectConfig(
                        globalEnabled = liquidGlassGlobalEnabled && useFloatingNavBar,
                        glassAvailable = liquidGlassGlobalEnabled,
                    )
                }
                
                val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background
                val appBackdrop = rememberLayerBackdrop {
                    drawRect(baseBg)
                    drawContent()
                }

                CompositionLocalProvider(
                    LocalDatabase provides database,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides getShimmerTheme(),
                    LocalSyncUtils provides syncUtils,
                    com.shiny.music.ui.liquid.together.LocalTogether provides together,
                    LocalGlassEffectConfig provides glassEffectConfig,
                    LocalAppBackdrop provides appBackdrop,
                    LocalPlayerExpansion provides playerExpansionState,
                    LocalLiquidBottomInset provides liquidBottomInset,
                ) {

                    // The shared-transition ancestor, deliberately inert for now.
                    //
                    // The player lives in the Scaffold's bottomBar and the NavHost in its
                    // content slot, so nothing above them was common to both — and a shared
                    // element needs a scope that encloses both ends of the transition. That
                    // was the structural blocker to a mini-player-to-player morph, and to
                    // list-item-to-player artwork continuity.
                    //
                    // Wrapping the whole Scaffold supplies that ancestor without moving
                    // either half: Scaffold is one layout node beneath this, so both slots
                    // are inside the scope even though they are measured separately. No
                    // sharedElement modifiers are applied yet, which is why this changes
                    // nothing on screen — SharedTransitionLayout with no shared keys is a
                    // Box.
                    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalSharedTransitionScope provides this,
                        ) {
                            Scaffold(
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                // Every Liquid page draws its own large title or floating bar.
                                topBar = {},
                                bottomBar = {
                                    val onNavItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                        { screen: Screens, isSelected: Boolean ->
                                            if (playerBottomSheetState.isExpanded) {
                                                playerBottomSheetState.collapseSoft()
                                            }

                                            if (isSelected) {
                                                navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                coroutineScope.launch {
                                                    topAppBarScrollBehavior.state.resetHeightOffset()
                                                }
                                            } else {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    }

                                    if (!chromeHidden) {
                                        Box(Modifier.fillMaxWidth()) {
                                            val tabScreens = remember(navigationItems) {
                                                navigationItems.filter { it != Screens.Search }
                                            }
                                            val tabLabels = tabScreens.map { stringResource(it.titleId) }
                                            val liquidTabs = remember(tabScreens, tabLabels) {
                                                tabScreens.mapIndexed { index, screen ->
                                                    LiquidTab(screen.route, tabLabels[index], liquidTabIcon(screen))
                                                }
                                            }
                                            // A pushed page (an album opened from Home) keeps its tab lit, as
                                            // on iOS; only switching tabs moves the selection.
                                            var lastTabRoute by rememberSaveable { mutableStateOf(Screens.Home.route) }
                                            val matchedRoute = navigationItems.firstOrNull { screen ->
                                                currentRoute == screen.route
                                            }?.route ?: if (currentRoute?.startsWith("search/") == true) Screens.Search.route else null
                                            LaunchedEffect(matchedRoute) {
                                                if (matchedRoute != null) lastTabRoute = matchedRoute
                                            }
                                            LiquidTabBar(
                                                tabs = liquidTabs,
                                                selectedRoute = matchedRoute ?: lastTabRoute,
                                                searchRoute = if (navigationItems.contains(Screens.Search)) Screens.Search.route else null,
                                                onTabClick = { tab, reselect ->
                                                    tabScreens.firstOrNull { it.route == tab.route }?.let { onNavItemClick(it, reselect && matchedRoute != null) }
                                                },
                                                onSearchClick = { reselect -> onNavItemClick(Screens.Search, reselect && matchedRoute != null) },
                                                backdrop = appBackdrop,
                                                chromeState = liquidChromeState,
                                                showTabs = showLiquidTabs,
                                                showAccessory = showLiquidAccessory,
                                                onAccessoryClick = { playerBottomSheetState.expandSoft() },
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(horizontal = 14.dp)
                                                    .padding(bottom = bottomInset + LiquidChromeBottomMargin)
                                                    .graphicsLayer {
                                                        // Read in the layer: the sheet moves every frame of a drag.
                                                        val p = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                        translationY = p * (size.height + (bottomInset + LiquidChromeBottomMargin).toPx())
                                                        alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                                                    },
                                            )

                                            // The player card rises over the chrome it came from.
                                            LiquidPlayerSheet(
                                                state = playerBottomSheetState,
                                                navController = navController,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                    .nestedScroll(liquidChromeState.nestedScrollConnection)
                            ) {
                                // The same condition as the page's alpha below, for the page's own animations: they
                                // rest while nothing of them is drawn (NowPlayingBars).
                                val pageCovered = remember(playerBottomSheetState, playerConnection) {
                                    derivedStateOf { playerConnection != null && playerBottomSheetState.isExpanded }
                                }
                                CompositionLocalProvider(LocalPageCovered provides pageCovered) {
                                    Row(
                                        Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                // Fully open, the player is opaque edge to edge (its corners
                                                // are square at rest), so the page beneath cannot be seen.
                                                // Skipping it spares the RenderThread redrawing the page and
                                                // its glass on every frame the player animates. Any drag
                                                // leaves `isExpanded` and brings it straight back.
                                                alpha = if (playerConnection != null && playerBottomSheetState.isExpanded) 0f else 1f
                                            }
                                    ) {
                                        val onRailItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                            { screen: Screens, isSelected: Boolean ->
                                                if (playerBottomSheetState.isExpanded) {
                                                    playerBottomSheetState.collapseSoft()
                                                }

                                                if (isSelected) {
                                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                    coroutineScope.launch {
                                                        topAppBarScrollBehavior.state.resetHeightOffset()
                                                    }
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        }

                                        val onRailSearchLongClick: () -> Unit = remember(navController) {
                                            {
                                                navController.navigate("recognition") {
                                                    launchSingleTop = true
                                                }
                                            }
                                        }

                                        if (showRail && currentRoute != "update") {
                                            LiquidRail(
                                                items = navigationItems,
                                                currentRoute = currentRoute,
                                                onItemClick = onRailItemClick,
                                                onSearchLongClick = onRailSearchLongClick,
                                            )
                                        }
                                        Box(Modifier.weight(1f)) {
                                            // Read here, not in the transition lambdas, so a change
                                            // in Appearance re-creates them once.
                                            val pageTransitions = LocalShinyAppearance.current.transitions
                                            NavHost(
                                                navController = navController,
                                                startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                                    NavigationTab.HOME -> Screens.Home
                                                    NavigationTab.LIBRARY -> Screens.Library
                                                    else -> Screens.Home
                                                }.route,
                                    
                                                // iOS navigation: switching tabs is a quick dissolve, drilling
                                                // in pushes the new page over the old one, which slides a third
                                                // of the way under it. Predictive back scrubs the same pair.
                                                enterTransition = {
                                                    LiquidNav.enter(pageTransitions, isTabSwitch(initialState.destination.route, targetState.destination.route, navigationItems))
                                                },
                                                exitTransition = {
                                                    LiquidNav.exit(pageTransitions, isTabSwitch(initialState.destination.route, targetState.destination.route, navigationItems))
                                                },
                                                popEnterTransition = {
                                                    LiquidNav.popEnter(pageTransitions, isTabSwitch(initialState.destination.route, targetState.destination.route, navigationItems))
                                                },
                                                popExitTransition = {
                                                    LiquidNav.popExit(pageTransitions, isTabSwitch(initialState.destination.route, targetState.destination.route, navigationItems))
                                                },
                                                modifier = Modifier
                                                    .layerBackdrop(appBackdrop)
                                                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                            ) {
                                                navigationBuilder(
                                                    navController = navController,
                                                    scrollBehavior = topAppBarScrollBehavior,
                                                    activity = this@MainActivity,
                                                    snackbarHostState = snackbarHostState
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // The artwork in transit between the mini player and the full one.
                    //
                    // It sits here, above the shared-transition layout, because there is no
                    // point inside either player that both ends can be drawn from: the
                    // sheet clips to its rounded top, the artwork carousel clips to its
                    // viewport, and in the default configuration the mini player is docked
                    // in the floating tab bar rather than in the sheet at all. It draws
                    // nothing unless the sheet is between its two anchors, and it consumes
                    // no touches at any point.
                    PlayerArtworkFlight()

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    BottomSheetPage(
                        state = LocalBottomSheetPageState.current,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )



                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = { sharedSong = null },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!onboardingCompleted) {
                        com.shiny.music.ui.liquid.screens.LiquidOnboarding(
                            onFinish = {
                                setOnboardingCompleted(true)
                                // Stamp the version too, so the very next launch does not
                                // immediately show the update dialog for this same build.
                                setLastOpenedVersionCode(BuildConfig.VERSION_CODE)
                            }
                        )
                    }

                    if (showWelcomeDialog) {
                        com.shiny.music.ui.liquid.screens.LiquidWhatsNew(
                            onDismiss = {
                                showWelcomeDialog = false
                                setLastOpenedVersionCode(BuildConfig.VERSION_CODE)
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Brings an existing local library in line with the current rules, once per process,
     * well after launch: a light look at the audio index, and a scan only if it or the
     * source settings changed since the last one. This is what removes voice notes and
     * recordings from a library scanned before music-only discovery, without waiting for
     * the user to open On This Device. It never runs for someone who has not used the
     * local library, or without the audio permission, and never touches the first frame.
     */
    private fun scheduleLocalLibraryCheck() {
        if (localLibraryChecked) return
        localLibraryChecked = true
        lifecycleScope.launch(Dispatchers.IO) {
            delay(LOCAL_LIBRARY_CHECK_DELAY_MS)
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_AUDIO
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, permission) != PackageManager.PERMISSION_GRANTED) return@launch
            if (database.localSongIds().isEmpty()) return@launch
            while (!com.shiny.music.utils.PreferencesSnapshot.isLoaded) delay(250)
            runCatching {
                val scanner = localSongScanner.get()
                val config = com.shiny.music.localmedia.currentLocalScanConfig()
                if (scanner.needsScan(config)) scanner.scanDevice(config, automatic = true)
            }.onFailure { Timber.tag("LocalLibrary").w(it, "Background library check failed") }
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        val sharedText = intent.extras?.getString(Intent.EXTRA_TEXT)
        var uri = intent.data
        if (uri == null) {
            val extraText = intent.extras?.getString(Intent.EXTRA_TEXT)
            if (extraText != null) {
                val urlRegex = "(https?://[^\\s]+)".toRegex()
                val match = urlRegex.find(extraText)
                if (match != null) {
                    uri = match.value.toUri()
                }
            }
        }
        if (uri == null) return
        // shinymusic://together opens Listen Together (the join-request notification sends it).
        if (uri.scheme == "shinymusic" && uri.host.equals("together", ignoreCase = true)) {
            intent.data = null
            navController.navigate("together")
            return
        }
        // shinymusic://watch?v=… (and playlist, channel, u) come from Shiny's web pages: read them as the same
        // paths on the website. shinymusic://listen keeps its own handling below.
        if (uri.scheme == "shinymusic" && uri.host != null && !uri.host.equals("listen", ignoreCase = true)) {
            uri = uri.buildUpon()
                .scheme("https")
                .authority(com.shiny.music.social.ShinyLinks.WEB_HOST)
                .path("/${uri.host}${uri.path.orEmpty()}")
                .build()
        }

        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope

        // Listen Together invites: shinymusic://listen?code=…, …/listen?code=… and the
        // Together server's own …/j/CODE pages.
        val listenCode = uri.getQueryParameter("code")
            ?: uri.getQueryParameter("room")
            ?: uri.pathSegments.getOrNull(1)
        val isListenLink = uri.pathSegments.firstOrNull() == "listen" ||
            uri.pathSegments.firstOrNull() == "j" ||
            uri.host?.equals("listen", ignoreCase = true) == true
        if (!listenCode.isNullOrBlank() && isListenLink) {
            together.join(listenCode)
            navController.navigate("together")
            return
        }

        // Shared from a service whose links Shiny can't read (Amazon Music, Apple Music): the
        // share text names the song, so that becomes a search.
        val sharedHost = uri.host.orEmpty().lowercase()
        if (sharedHost.contains("amazon.") || sharedHost.startsWith("amzn.") || sharedHost == "music.apple.com") {
            sharedSongQuery(sharedText)?.let { query ->
                navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
            }
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                withContext(Dispatchers.Main) {
                                    navController.navigate("album/$browseId")
                                }
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            // A Shiny profile link: offer to add them as a friend.
            "u" -> uri.pathSegments.getOrNull(1)?.let { username ->
                navController.navigate("friends?add=${android.net.Uri.encode(username)}")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                }
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" || uri.host == "share.shinymusic.fun" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")
                // Listen-along and YouTube links can carry a start time: t=83 or t=83s.
                val startPositionMs = uri.getQueryParameter("t")?.removeSuffix("s")?.toLongOrNull()
                    ?.takeIf { it > 0L }?.times(1000L) ?: 0L

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(listOf(videoId), playlistId).onSuccess { queue ->
                            withContext(Dispatchers.Main) {
                                var attempts = 0
                                while (playerConnection == null && attempts < 20) {
                                    delay(100)
                                    attempts++
                                }
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                        queue.firstOrNull()?.toMediaMetadata()
                                    ),
                                    startPositionMs = startPositionMs,
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(null, playlistId).onSuccess { queue ->
                            val firstItem = queue.firstOrNull()
                            withContext(Dispatchers.Main) {
                                var attempts = 0
                                while (playerConnection == null && attempts < 20) {
                                    delay(100)
                                    attempts++
                                }
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                        firstItem?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    /** "Check out “Song” by Artist on Amazon Music https://…" → "Song Artist". */
    private fun sharedSongQuery(text: String?): String? {
        val cleaned = text.orEmpty()
            .replace(Regex("""https?://\S+"""), " ")
            .replace(Regex("""(?i)\bon (amazon|apple) music\b"""), " ")
            .replace(Regex("""(?i)^\s*(check out|listen to|i'm listening to|i am listening to|listening to)\s+"""), "")
            .replace(Regex("""[“”"«»]"""), " ")
            .replace(Regex("""(?i)\s+by\s+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', '!', ':', '-', '–')
        return cleaned.takeIf { it.length >= 2 }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        requestedDarkBars = isDark
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            // The launch artwork is pale at the top and night at the bottom, whatever the theme.
            isAppearanceLightStatusBars = if (launchArtVisible) true else !isDark
            isAppearanceLightNavigationBars = if (launchArtVisible) false else !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
    private fun handleRecognitionIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action != ACTION_RECOGNITION) return
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)

        intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
        navController.navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
            launchSingleTop = true
        }
    }

    private fun handleAssistantSearchIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY) ?: return
            navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
        }
    }
}

/**
 * The shared-transition scope that encloses both the NavHost and the player.
 *
 * Provided by the [SharedTransitionLayout] wrapping the app's Scaffold. Nothing consumes
 * it yet; it exists so that the player and the screens beneath it can later name the same
 * shared elements — artwork, in particular — without either having to reach for a scope
 * the other cannot see.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }

val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalIsPlayerExpanded = compositionLocalOf { false }
