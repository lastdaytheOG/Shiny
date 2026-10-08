

package com.shiny.music.ui.screens

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiny.music.ui.liquid.LocalLiquidColors
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.shiny.music.constants.DarkModeKey
import com.shiny.music.constants.PureBlackKey
import com.shiny.music.ui.screens.equalizer.EqScreen
import com.shiny.music.ui.screens.settings.BackupAndRestore
import com.shiny.music.ui.screens.settings.ContentSettings
import com.shiny.music.ui.screens.settings.DarkMode
import com.shiny.music.ui.screens.settings.RomanizationSettings
import com.shiny.music.ui.screens.settings.AccountSettingsScreen
import com.shiny.music.ui.liquid.settings.AboutSettingsScreen
import com.shiny.music.ui.liquid.settings.AdvancedSettingsScreen
import com.shiny.music.ui.liquid.settings.AppearanceSettingsScreen
import com.shiny.music.ui.liquid.settings.LibraryHomeSettingsScreen
import com.shiny.music.ui.liquid.settings.LyricsSettingsScreen
import com.shiny.music.ui.liquid.settings.NowPlayingSettingsScreen
import com.shiny.music.ui.liquid.settings.PlaybackSettingsScreen
import com.shiny.music.ui.liquid.settings.PrivacySettingsScreen
import com.shiny.music.ui.liquid.settings.SettingsHomeScreen
import com.shiny.music.ui.liquid.settings.SpotifyImportScreen
import com.shiny.music.ui.liquid.settings.StorageSettingsScreen

import com.shiny.music.shinymusic.updater.UpdateScreen
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import com.shiny.music.ui.screens.equalizer.axion.AxionEqScreen
import com.shiny.music.ui.screens.ambient.AmbientModeScreen

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
    snackbarHostState: SnackbarHostState
) {
    screen(Screens.Home.route) {
        // The activity's instance, which MainActivity already holds for the account avatar. A second,
        // screen-scoped one ran the whole Home load again at every launch: local shelves, ~30
        // requests, the snapshot and the auto sync, twice.
        com.shiny.music.ui.liquid.screens.LiquidHomeScreen(
            navController = navController,
            viewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(
                viewModelStoreOwner = activity as androidx.activity.ComponentActivity,
            ),
        )
    }

    screen(Screens.Search.route) {
        com.shiny.music.ui.liquid.screens.LiquidSearchScreen(navController)
    }

    screen(Screens.Explore.route) {
        com.shiny.music.ui.liquid.screens.LiquidNewScreen(navController)
    }

    screen(Screens.Library.route) {
        com.shiny.music.ui.liquid.screens.LiquidLibraryScreen(navController)
    }

    screen("library/playlists") {
        com.shiny.music.ui.liquid.screens.LiquidLibraryPlaylistsScreen(navController)
    }

    screen("library/artists") {
        com.shiny.music.ui.liquid.screens.LiquidLibraryArtistsScreen(navController)
    }

    screen("library/albums") {
        com.shiny.music.ui.liquid.screens.LiquidLibraryAlbumsScreen(navController)
    }

    screen("library/songs") {
        com.shiny.music.ui.liquid.screens.LiquidLibrarySongsScreen(navController)
    }

    // Listen Together: its own tab (no back button) or pushed from Library, Now Playing,
    // Settings or an invite (with one).
    screen(Screens.ListenTogether.route) {
        val (togetherNotTab) = rememberPreference(com.shiny.music.constants.ListenTogetherInTopBarKey, true)
        com.shiny.music.ui.liquid.together.TogetherScreen(navController, showBack = togetherNotTab)
    }

    screen("together") {
        com.shiny.music.ui.liquid.together.TogetherScreen(navController, showBack = true)
    }

    screen("listen_together_from_topbar") {
        com.shiny.music.ui.liquid.together.TogetherScreen(navController, showBack = true)
    }

    screen("together/chat") {
        com.shiny.music.ui.liquid.together.TogetherChatScreen(navController)
    }

    screen("history") {
        com.shiny.music.ui.liquid.screens.LiquidHistoryScreen(navController)
    }

    screen("ambient_mode") {
        AmbientModeScreen(navController)
    }

    screen("local_songs") {
        com.shiny.music.ui.liquid.screens.LiquidLocalSongsScreen(navController)
    }

    screen("stats") {
        com.shiny.music.ui.liquid.screens.LiquidStatsScreen(navController)
    }

    screen("mood_and_genres") {
        com.shiny.music.ui.liquid.screens.LiquidMoodAndGenresScreen(navController)
    }

    screen("account") {
        com.shiny.music.ui.liquid.screens.LiquidAccountScreen(navController)
    }

    screen("new_release") {
        com.shiny.music.ui.liquid.screens.LiquidNewReleaseScreen(navController)
    }

    screen("charts_screen") {
        com.shiny.music.ui.liquid.screens.LiquidChartsScreen(navController)
    }

    screen(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        com.shiny.music.ui.liquid.screens.LiquidBrowseScreen(navController)
    }

    screen(
        route = "search/{query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidSearchResultScreen(navController)
    }

    screen(
        route = "album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidAlbumScreen(navController)
    }

    screen(
        route = "artist/{artistId}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidArtistScreen(navController)
    }

    screen(
        route = "artist/{artistId}/songs",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidArtistSongsScreen(navController)
    }

    screen(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        com.shiny.music.ui.liquid.screens.LiquidArtistAlbumsScreen(navController)
    }

    screen(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidArtistItemsScreen(navController)
    }

    screen(
        route = "online_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidOnlinePlaylistScreen(navController)
    }

    screen(
        route = "shared_playlist/{shareId}",
        arguments = listOf(
            navArgument("shareId") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidSharedPlaylistScreen(navController)
    }

    screen(
        route = "local_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidLocalPlaylistScreen(navController)
    }

    screen(
        route = "auto_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidAutoPlaylistScreen(navController)
    }

    screen(
        route = "cache_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidCachePlaylistScreen(navController)
    }

    screen(
        route = "top_playlist/{top}",
        arguments = listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidTopPlaylistScreen(navController)
    }

    screen(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        com.shiny.music.ui.liquid.screens.LiquidYouTubeBrowseScreen(navController)
    }

    screen("settings") {
        SettingsHomeScreen(navController)
    }

    // Kept as an alias: anything that still points at the old updates page lands on About,
    // which is where the updater's settings live now.
    screen("settings/update") {
        AboutSettingsScreen(navController)
    }

    screen("settings/account") {
        AccountSettingsScreen(navController, scrollBehavior)
    }

    screen("settings/appearance") {
        AppearanceSettingsScreen(navController)
    }

    screen("settings/now_playing") {
        NowPlayingSettingsScreen(navController)
    }

    screen("settings/lyrics") {
        LyricsSettingsScreen(navController)
    }

    screen("settings/library") {
        LibraryHomeSettingsScreen(navController)
    }

    screen("settings/library/charts") {
        com.shiny.music.ui.liquid.settings.HomeChartsSettingsScreen(navController)
    }

    screen("settings/library/sources") {
        com.shiny.music.ui.liquid.settings.MusicSourcesSettingsScreen(navController)
    }

    screen("settings/privacy") {
        PrivacySettingsScreen(navController)
    }
    screen("settings/supported_links") {
        com.shiny.music.ui.liquid.settings.SupportedLinksScreen(navController)
    }

    screen("settings/advanced") {
        AdvancedSettingsScreen(navController)
    }

    screen("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }

    screen("uptime") {
        com.shiny.music.ui.liquid.settings.ServiceStatusScreen(navController)
    }

    screen("settings/content/romanization") {
        RomanizationSettings(navController, scrollBehavior)
    }

    // "settings/player" stays the route for Playback so existing shortcuts still land.
    screen("settings/player") {
        PlaybackSettingsScreen(navController)
    }

    screen("settings/storage") {
        StorageSettingsScreen(navController = navController)
    }

    screen("settings/equalizer") {
        AxionEqScreen(onBackClick = { navController.navigateUp() })
    }

    screen("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }

    screen(
        route = "settings/spotify_import?login={login}",
        arguments = listOf(navArgument("login") { type = NavType.BoolType; defaultValue = false })
    ) { backStackEntry ->
        SpotifyImportScreen(navController, openLogin = backStackEntry.arguments?.getBoolean("login") == true)
    }

    screen("settings/together") {
        com.shiny.music.ui.liquid.settings.TogetherSettingsScreen(navController)
    }

    screen("settings/integrations/listen_together") {
        com.shiny.music.ui.liquid.settings.TogetherSettingsScreen(navController)
    }

    screen("settings/social") {
        com.shiny.music.ui.liquid.screens.SocialSettingsScreen(navController)
    }

    screen("settings/discord") {
        com.shiny.music.ui.liquid.screens.DiscordSettingsScreen(navController)
    }

    // `add` comes from a profile link (shinymusic.in/u/<username>): offer to add them.
    screen(
        route = "friends?add={add}",
        arguments = listOf(
            navArgument("add") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { entry ->
        com.shiny.music.ui.liquid.screens.FriendsScreen(navController, addUsername = entry.arguments?.getString("add"))
    }

    screen("settings/about") {
        AboutSettingsScreen(navController)
    }

    screen("update") {
        UpdateScreen(navController)
    }

    screen("login") {
        LoginScreen(navController)
    }

    dialog("equalizer") {
        EqScreen(navController = navController)
    }

    screen("recognition") {
        com.shiny.music.ui.liquid.screens.LiquidRecognitionScreen(navController)
    }

    screen("recognition_history") {
        com.shiny.music.ui.liquid.screens.LiquidRecognitionHistoryScreen(navController)
    }
    screen("settings/changelog") {
        com.shiny.music.ui.liquid.settings.WhatsNewScreen(navController)
    }
}

/**
 * A destination on an opaque page.
 *
 * The iOS push slides the new page over the old one, and a destination without its own
 * ground would let one screen show through the other for the whole transition.
 */
fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        val scope = this
        val colors = LocalLiquidColors.current
        val ground = if (route.startsWith("settings") || route == "account") colors.groupedBackground else colors.background
        androidx.compose.runtime.CompositionLocalProvider(
            com.shiny.music.ui.liquid.LocalLiquidPageGround provides ground,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ground)
            ) {
                scope.content(entry)
            }
        }
    }
}
