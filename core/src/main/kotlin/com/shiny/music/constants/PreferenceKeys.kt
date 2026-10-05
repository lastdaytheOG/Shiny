

package com.shiny.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

import com.music.innertube.models.IpVersion

val DataSaverEnabledKey = booleanPreferencesKey("dataSaverEnabled")
val SpotifySpDcKey = stringPreferencesKey("spotify_sp_dc")
val SpotifySpKeyKey = stringPreferencesKey("spotify_sp_key")
val SpotifyAccountNameKey = stringPreferencesKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringPreferencesKey("spotify_account_avatar_url")
val SpotifyAccessTokenKey = stringPreferencesKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longPreferencesKey("spotify_access_token_expires_at")

/** Show the Spotify account's own mixes (Daily Mix, Discover Weekly...) on Home. Default on. */
val SpotifyHomeMixesKey = booleanPreferencesKey("spotify_home_mixes")
val EnableHighRefreshRateKey = booleanPreferencesKey("enableHighRefreshRate")
val EnableHapticsKey = booleanPreferencesKey("enableHaptics")
val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val SelectedThemeColorKey = intPreferencesKey("selectedThemeColor")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val DensityScaleKey = floatPreferencesKey("density_scale_factor")

/**
 * How tightly the interface is packed.
 *
 * The values are the scale factor `com.dpi.DensityScaler` applies to the display density
 * at process start, so they cannot change without moving every existing user's layout;
 * only the names people read are Shiny's own. Values above 1 enlarge the interface;
 * the scaler caps them on narrow screens.
 */
enum class DensityScale(val value: Float, val label: String) {
    NATIVE(1.0f, "Default"),
    SLIGHTLY_COMPACT(0.85f, "Snug"),
    COMPACT(0.75f, "Compact"),
    VERY_COMPACT(0.65f, "Dense"),
    ULTRA_COMPACT(0.55f, "Densest"),
    COMFORTABLE(1.1f, "Comfortable"),
    LARGE(1.2f, "Large");

    companion object {
        fun fromValue(value: Float): DensityScale = entries.find { it.value == value } ?: NATIVE

        /** Smallest to largest, the order a size picker lists them in. */
        val bySize: List<DensityScale> get() = entries.sortedBy { it.value }
    }
}

val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val UseNewMiniPlayerDesignKey = booleanPreferencesKey("useNewMiniPlayerDesign")
val CropAlbumArtKey = booleanPreferencesKey("cropAlbumArt")
val PauseOnMute = booleanPreferencesKey("pauseOnMute")
val ResumeOnBluetoothConnectKey = booleanPreferencesKey("resumeOnBluetoothConnect")
val KeepScreenOn = booleanPreferencesKey("keepScreenOn")


const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val FetchFasterLyricsKey = booleanPreferencesKey("fetchFasterLyrics")

val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val EnableSimpMusicKey = booleanPreferencesKey("enableSimpMusic")
val EnableYouLyPlusKey = booleanPreferencesKey("enableYouLyPlus")
val EnablePaxsenixKey = booleanPreferencesKey("enablePaxsenix")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")
val HideYoutubeShortsKey = booleanPreferencesKey("hideYoutubeShorts")
val ShowArtistBackgroundVideoKey = booleanPreferencesKey("showArtistBackgroundVideo")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val YtmSyncKey = booleanPreferencesKey("ytmSync")

val AudioQualityKey = stringPreferencesKey("audioQuality")
val IpVersionKey = stringPreferencesKey("ipVersion")

enum class AudioQuality {
    OPUS,
}

val DownloadQualityKey = stringPreferencesKey("downloadQuality")

enum class DownloadQuality {
    YOUTUBE,
}

val AudioOffload = booleanPreferencesKey("enableOffload")

enum class PlaybackEngine {
    POTOKEN,
    BRAVEPIPE,
    AUTO,
}

val PlaybackEngineKey = stringPreferencesKey("playbackEngine")
val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PersistentShuffleAcrossQueuesKey = booleanPreferencesKey("persistentShuffleAcrossQueues")
val RememberShuffleAndRepeatKey = booleanPreferencesKey("rememberShuffleAndRepeat")
val ShuffleModeKey = booleanPreferencesKey("shuffleMode")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val SkipSilenceInstantKey = booleanPreferencesKey("skipSilenceInstant")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
/** The player's right-hand time: the time left ("-1:23") when true, the song's length when false. */
val PlayerShowRemainingTimeKey = booleanPreferencesKey("playerShowRemainingTime")
val DisableLoadMoreWhenRepeatAllKey = booleanPreferencesKey("disableLoadMoreWhenRepeatAll")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val SimilarContent = booleanPreferencesKey("similarContent")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val ShufflePlaylistFirstKey = booleanPreferencesKey("shufflePlaylistFirst")
val PreventDuplicateTracksInQueueKey = booleanPreferencesKey("preventDuplicateTracksInQueue")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfadeEnabled")
val CrossfadeDurationKey = floatPreferencesKey("crossfadeDuration")
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")


val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")
val ExportingSongIdsKey = stringPreferencesKey("exportingSongIds")
val ExportedSongIdsKey = stringPreferencesKey("exportedSongIds")
// Comma-separated "songId:percent" pairs, e.g. "abc:42,def:87"
val ExportProgressKey = stringPreferencesKey("exportProgress")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")






// Listen Together (Shiny Together). The first two keep their old names so a chosen display
// name and tab placement survive the rebuild.
val ListenTogetherUsernameKey = stringPreferencesKey("listenTogetherUsername")
/** True: Together lives in Library; false: it has its own tab. */
val ListenTogetherInTopBarKey = booleanPreferencesKey("listenTogetherInTopBar")
/** Overrides the built-in Together server (https://…), for self-hosting and local testing. */
val TogetherServerUrlKey = stringPreferencesKey("togetherServerUrl")
/** How a guest listens by default: "phone" (in sync on this phone) or "remote" (host's speaker). */
val TogetherModeKey = stringPreferencesKey("togetherMode")
/** Extra audio delay to compensate for, in ms (Bluetooth headphones play late). */
val TogetherLatencyKey = intPreferencesKey("togetherLatencyMs")
val TogetherDeviceIdKey = stringPreferencesKey("togetherDeviceId")
/** The session to rejoin after the app was closed mid-session: code, token, time. */
val TogetherResumeKey = stringPreferencesKey("togetherResume")
val TogetherRequestAlertsKey = booleanPreferencesKey("togetherRequestAlerts")
/** Friends' reactions float over Now Playing. */
val TogetherPlayerReactionsKey = booleanPreferencesKey("togetherPlayerReactions")

val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val AddToPlaylistSortTypeKey = stringPreferencesKey("addToPlaylistSortType")
val AddToPlaylistSortDescendingKey = booleanPreferencesKey("addToPlaylistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")

val LocalSongsMinDurationSecondsKey = intPreferencesKey("local_songs_min_duration_seconds")
val LocalSongsExcludedFoldersKey = stringSetPreferencesKey("local_songs_excluded_folders")

/** Folders whose audio is always music, even where the default skips would leave it out. */
val LocalSongsIncludedFoldersKey = stringSetPreferencesKey("local_songs_included_folders")

/**
 * Keys of the non-music sources left out of the local library (`SkippedSource.key`).
 * Absent means every source is skipped; an empty set means none is.
 */
val LocalSongsSkippedSourcesKey = stringSetPreferencesKey("local_songs_skipped_sources")

/**
 * Local songs that a scan left out but kept in the database because the user had played,
 * liked, saved or playlisted them. Hidden from On This Device; their history stays.
 */
val LocalSongsHiddenIdsKey = stringSetPreferencesKey("local_songs_hidden_ids")

/** What the last completed scan saw: filter version, audio index fingerprint and settings. */
val LocalSongsScanStampKey = stringPreferencesKey("local_songs_scan_stamp")
val DiscordTokenKey = stringPreferencesKey("discord_token")
val DiscordRefreshTokenKey = stringPreferencesKey("discord_refresh_token")
val DiscordTokenExpiresAtKey = longPreferencesKey("discord_token_expires_at")
/** The Discord application the stored session was issued to; a session from any other one needs reconnecting. */
val DiscordClientIdKey = stringPreferencesKey("discord_client_id")
val DiscordNameKey = stringPreferencesKey("discord_name")
val DiscordUsernameKey = stringPreferencesKey("discord_username")
val DiscordAvatarUrlKey = stringPreferencesKey("discord_avatar_url")
val EnableDiscordRPCKey = booleanPreferencesKey("enable_discord_rpc")
val DiscordShowWhenPausedKey = booleanPreferencesKey("discord_show_when_paused")
val DiscordPresenceStatusKey = stringPreferencesKey("discord_presence_status")
val DiscordActivityTypeKey = stringPreferencesKey("discord_activity_type")
val DiscordActivityNameKey = stringPreferencesKey("discord_activity_name")
val DiscordActivityDetailsKey = stringPreferencesKey("discord_activity_details")
val DiscordActivityStateKey = stringPreferencesKey("discord_activity_state")
val DiscordActivityPlatformKey = stringPreferencesKey("discord_activity_platform")
val DiscordLargeImageTypeKey = stringPreferencesKey("discord_large_image_type")
val DiscordLargeImageCustomUrlKey = stringPreferencesKey("discord_large_image_custom_url")

val DiscordSmallImageTypeKey = stringPreferencesKey("discord_small_image_type")
val DiscordSmallImageCustomUrlKey = stringPreferencesKey("discord_small_image_custom_url")
val DiscordActivityButton1EnabledKey = booleanPreferencesKey("discord_activity_button1_enabled")
val DiscordActivityButton1LabelKey = stringPreferencesKey("discord_activity_button1_label")
val DiscordActivityButton1UrlSourceKey = stringPreferencesKey("discord_activity_button1_url_source")
val DiscordActivityButton1CustomUrlKey = stringPreferencesKey("discord_activity_button1_custom_url")
val DiscordActivityButton2EnabledKey = booleanPreferencesKey("discord_activity_button2_enabled")
val DiscordActivityButton2LabelKey = stringPreferencesKey("discord_activity_button2_label")
val DiscordActivityButton2UrlSourceKey = stringPreferencesKey("discord_activity_button2_url_source")
val DiscordActivityButton2CustomUrlKey = stringPreferencesKey("discord_activity_button2_custom_url")
/** The Discord activity's first button opens the song where you are in it, for anyone who taps it. */
val DiscordListenAlongButtonKey = booleanPreferencesKey("discord_listen_along_button")

// Shiny social: Google sign-in, friends, live profile (server in /server)
val SocialSessionTokenKey = stringPreferencesKey("social_session_token")
val SocialUserJsonKey = stringPreferencesKey("social_user_json")
/** While on, nothing is shared: friends and the public profile see you as not listening. */
val SocialPrivateSessionKey = booleanPreferencesKey("social_private_session")
/** Debug builds only: point the app at another social server, e.g. `http://10.0.2.2:8787` for `wrangler dev`. */
val SocialServerUrlKey = stringPreferencesKey("social_server_url")
val LocalSongsSortTypeKey = stringPreferencesKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = booleanPreferencesKey("local_songs_sort_descending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val LastFullSyncKey = longPreferencesKey("last_full_sync")



const val SYNC_COOLDOWN = 30 * 60L


val QuickPicksKey = stringPreferencesKey("discover")
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val LyricsProviderOrderKey = stringPreferencesKey("lyricsProviderOrder")

val ShowSpeedDialKey = booleanPreferencesKey("showSpeedDial")

/** Which country's YouTube chart Home shows: "AUTO" (the listener's location), "OFF", "ZZ" (Global) or a country code. */
val HomeChartsCountryKey = stringPreferencesKey("homeChartsCountry")

/** Whether Home shows the Global chart beside the chosen country's. */
val HomeChartsGlobalKey = booleanPreferencesKey("homeChartsGlobal")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    UPLOADED,
    EXPORTED
}

enum class ArtistFilter {
    LIBRARY,
    LIKED
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    UPLOADED
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY ->
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            WEEK ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL_TIME -> 0
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

enum class PreferredLyricsProvider {
    LRCLIB,
    KUGOU,
    BETTER_LYRICS,
    SIMPMUSIC,
    YOULYPLUS,
    PAXSENIX,
    UNISON
}


enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    BLUR,
    GLOW_ANIMATED,
    APPLE_MUSIC,
    LIVE_MESH,
    LIQUID_GLASS,
    GLASSMORPHISM,
}

val TopSize = stringPreferencesKey("topSize")
val HistoryDuration = floatPreferencesKey("historyDuration")

val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val ShowLyricsOnPlayerKey = booleanPreferencesKey("showLyricsOnPlayer")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeRussianKey = booleanPreferencesKey("lyricsRomanizeRussian")
val LyricsRomanizeUkrainianKey = booleanPreferencesKey("lyricsRomanizeUkrainian")
val LyricsRomanizeSerbianKey = booleanPreferencesKey("lyricsRomanizeSerbian")
val LyricsRomanizeBulgarianKey = booleanPreferencesKey("lyricsRomanizeBulgarian")
val LyricsRomanizeBelarusianKey = booleanPreferencesKey("lyricsRomanizeBelarusian")
val LyricsRomanizeKyrgyzKey = booleanPreferencesKey("lyricsRomanizeKyrgyz")
val LyricsRomanizeMacedonianKey = booleanPreferencesKey("lyricsRomanizeMacedonian")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizePunjabiKey = booleanPreferencesKey("lyricsRomanizePunjabi")
val LyricsRomanizeAsMainKey = booleanPreferencesKey("lyricsRomanizeAsMain")
val LyricsRomanizeCyrillicByLineKey = booleanPreferencesKey("lyricsRomanizeCyrillicByLine")



val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")



/**
 * Idle motion applied to the player artwork ("poster").
 *
 * [PULSE] is the default because it is what the player did before this setting
 * existed — a barely-there breathing scale. The rest are opt-in.
 *
 * [RotatingThumbnailKey] is deliberately *not* folded in here: it also changes the
 * artwork's clip shape, users already have it switched on, and it takes precedence
 * over this setting when enabled.
 */


/**
 * Strength of tactile feedback on transport controls, seeking and track changes.
 *
 * Android has no volume control for haptics, only a choice of predefined effects, so the
 * levels map to different `HapticFeedbackConstants` rather than scaling one amplitude.
 */



/**
 * Arrangement of the now playing screen. Distinct from [PlayerBackgroundStyle], which
 * only paints behind whichever of these is chosen.
 */

val OnboardingCompletedKey = booleanPreferencesKey("onboardingCompletedV2")


/**
 * How much icons react when they are selected or pressed. Scales the amplitude of
 * the shared spring in `ui/theme/IconMotion.kt`; [NONE] disables it outright for
 * users who find animated chrome distracting.
 */

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")
val LastOpenedVersionCodeKey = intPreferencesKey("lastOpenedVersionCode")

val LanguageCodeToName =
    mapOf(
        "af" to "Afrikaans",
        "az" to "Azərbaycan",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Malaysia",
        "ca" to "Català",
        "cs" to "Čeština",
        "da" to "Dansk",
        "de" to "Deutsch",
        "et" to "Eesti",
        "en-GB" to "English (UK)",
        "en" to "English (US)",
        "es" to "Español (España)",
        "es-419" to "Español (Latinoamérica)",
        "eu" to "Euskara",
        "fil" to "Filipino",
        "fr" to "Français",
        "fr-CA" to "Français (Canada)",
        "gl" to "Galego",
        "hr" to "Hrvatski",
        "zu" to "IsiZulu",
        "is" to "Íslenska",
        "it" to "Italiano",
        "sw" to "Kiswahili",
        "lt" to "Lietuvių",
        "hu" to "Magyar",
        "nl" to "Nederlands",
        "no" to "Norsk",
        "or" to "Odia",
        "uz" to "O‘zbe",
        "pl" to "Polski",
        "pt-PT" to "Português",
        "pt" to "Português (Brasil)",
        "ro" to "Română",
        "sq" to "Shqip",
        "sk" to "Slovenčina",
        "sl" to "Slovenščina",
        "fi" to "Suomi",
        "sv" to "Svenska",
        "bo" to "Tibetan བོད་སྐད།",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "bg" to "Български",
        "ky" to "Кыргызча",
        "kk" to "Қазақ Тілі",
        "mk" to "Македонски",
        "mn" to "Монгол",
        "ru" to "Русский",
        "sr" to "Српски",
        "uk" to "Українська",
        "el" to "Ελληνικά",
        "hy" to "Հայերեն",
        "iw" to "עברית",
        "ur" to "اردو",
        "ar" to "العربية",
        "fa" to "فارسی",
        "ne" to "नेपाली",
        "mr" to "मराठी",
        "hi" to "हिन्दी",
        "bn" to "বাংলা",
        "pa" to "ਪੰਜਾਬੀ",
        "gu" to "ગુજરાતી",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം",
        "si" to "සිංහල",
        "th" to "ภาษาไทย",
        "lo" to "ລາວ",
        "my" to "ဗမာ",
        "ka" to "ქართული",
        "am" to "አማርኛ",
        "km" to "ខ្មែរ",
        "zh-CN" to "中文 (简体)",
        "zh-TW" to "中文 (繁體)",
        "zh-HK" to "中文 (香港)",
        "ja" to "日本語",
        "ko" to "한국어",
    )

val CountryCodeToName =
    mapOf(
        "DZ" to "Algeria",
        "AR" to "Argentina",
        "AU" to "Australia",
        "AT" to "Austria",
        "AZ" to "Azerbaijan",
        "BH" to "Bahrain",
        "BD" to "Bangladesh",
        "BY" to "Belarus",
        "BE" to "Belgium",
        "BO" to "Bolivia",
        "BA" to "Bosnia and Herzegovina",
        "BR" to "Brazil",
        "BG" to "Bulgaria",
        "KH" to "Cambodia",
        "CA" to "Canada",
        "CL" to "Chile",
        "HK" to "Hong Kong",
        "CO" to "Colombia",
        "CR" to "Costa Rica",
        "HR" to "Croatia",
        "CY" to "Cyprus",
        "CZ" to "Czech Republic",
        "DK" to "Denmark",
        "DO" to "Dominican Republic",
        "EC" to "Ecuador",
        "EG" to "Egypt",
        "SV" to "El Salvador",
        "EE" to "Estonia",
        "FI" to "Finland",
        "FR" to "France",
        "GE" to "Georgia",
        "DE" to "Germany",
        "GH" to "Ghana",
        "GR" to "Greece",
        "GT" to "Guatemala",
        "HN" to "Honduras",
        "HU" to "Hungary",
        "IS" to "Iceland",
        "IN" to "India",
        "ID" to "Indonesia",
        "IQ" to "Iraq",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IT" to "Italy",
        "JM" to "Jamaica",
        "JP" to "Japan",
        "JO" to "Jordan",
        "KZ" to "Kazakhstan",
        "KE" to "Kenya",
        "KR" to "South Korea",
        "KW" to "Kuwait",
        "LA" to "Lao",
        "LV" to "Latvia",
        "LB" to "Lebanon",
        "LY" to "Libya",
        "LI" to "Liechtenstein",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "MK" to "Macedonia",
        "MY" to "Malaysia",
        "MT" to "Malta",
        "MX" to "Mexico",
        "ME" to "Montenegro",
        "MA" to "Morocco",
        "NP" to "Nepal",
        "NL" to "Netherlands",
        "NZ" to "New Zealand",
        "NI" to "Nicaragua",
        "NG" to "Nigeria",
        "NO" to "Norway",
        "OM" to "Oman",
        "PK" to "Pakistan",
        "PA" to "Panama",
        "PG" to "Papua New Guinea",
        "PY" to "Paraguay",
        "PE" to "Peru",
        "PH" to "Philippines",
        "PL" to "Poland",
        "PT" to "Portugal",
        "PR" to "Puerto Rico",
        "QA" to "Qatar",
        "RO" to "Romania",
        "RU" to "Russian Federation",
        "SA" to "Saudi Arabia",
        "SN" to "Senegal",
        "RS" to "Serbia",
        "SG" to "Singapore",
        "SK" to "Slovakia",
        "SI" to "Slovenia",
        "ZA" to "South Africa",
        "ES" to "Spain",
        "LK" to "Sri Lanka",
        "SE" to "Sweden",
        "CH" to "Switzerland",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        "TH" to "Thailand",
        "TN" to "Tunisia",
        "TR" to "Turkey",
        "UG" to "Uganda",
        "UA" to "Ukraine",
        "AE" to "United Arab Emirates",
        "GB" to "United Kingdom",
        "US" to "United States",
        "UY" to "Uruguay",
        "VE" to "Venezuela (Bolivarian Republic)",
        "VN" to "Vietnam",
        "YE" to "Yemen",
        "ZW" to "Zimbabwe",
    )

val SuggestionRegionSlugToName =
    mapOf(
        "system" to "System Default",
        "us" to "Global (USA)",
        "in" to "India",
        "gb" to "United Kingdom",
        "ca" to "Canada",
        "au" to "Australia",
        "jp" to "Japan",
        "kr" to "South Korea",
        "de" to "Germany",
        "fr" to "France",
        "br" to "Brazil",
        "mx" to "Mexico",
        "ru" to "Russia",
        "it" to "Italy",
        "es" to "Spain",
        "nl" to "Netherlands",
        "se" to "Sweden",
        "no" to "Norway",
        "dk" to "Denmark",
        "fi" to "Finland",
        "pl" to "Poland",
        "tr" to "Turkey",
        "za" to "South Africa",
        "ng" to "Nigeria",
        "id" to "Indonesia",
        "my" to "Malaysia",
        "ph" to "Philippines",
        "th" to "Thailand",
        "vn" to "Vietnam",
        "tw" to "Taiwan",
        "hk" to "Hong Kong",
        "sg" to "Singapore",
        "ar" to "Argentina",
        "co" to "Colombia",
        "cl" to "Chile",
        "pe" to "Peru",
        "eg" to "Egypt",
        "sa" to "Saudi Arabia",
        "ae" to "United Arab Emirates",
        "il" to "Israel"
    )

val ListenBrainzEnabledKey = booleanPreferencesKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringPreferencesKey("listenbrainz_token")
val UnisonLyricsEnabledKey = booleanPreferencesKey("unison_lyrics_enabled")
val PreloadNextSongEnabledKey = booleanPreferencesKey("preload_next_song_enabled")
val PreloadNextSongLimitKey = intPreferencesKey("preload_next_song_limit")
val PreloadLyricsEnabledKey = booleanPreferencesKey("preload_lyrics_enabled")

val LiquidGlassGlobalEnabledKey = booleanPreferencesKey("liquidGlassGlobalEnabled")
val LiquidGlassTextColorKey = intPreferencesKey("liquidGlassTextColor")
val LiquidGlassSurfaceTintColorKey = intPreferencesKey("liquidGlassSurfaceTintColor")
val LiquidGlassSurfaceOpacityKey = floatPreferencesKey("liquidGlassSurfaceOpacity")
val LiquidGlassVibrancyKey = floatPreferencesKey("liquidGlassVibrancy")
val LiquidGlassBlurRadiusKey = floatPreferencesKey("liquidGlassBlurRadius")
val LiquidGlassLensHeightKey = floatPreferencesKey("liquidGlassLensHeight")
val LiquidGlassLensAmountKey = floatPreferencesKey("liquidGlassLensAmount")
val LiquidGlassChromaticAberrationKey = booleanPreferencesKey("liquidGlassChromaticAberration")
val LiquidGlassDepthEffectKey = booleanPreferencesKey("liquidGlassDepthEffect")
val LiquidGlassPlayerEnabledKey = booleanPreferencesKey("liquidGlassPlayerEnabled")
val LiquidGlassMiniPlayerEnabledKey = booleanPreferencesKey("liquidGlassMiniPlayerEnabled")
val LiquidGlassNavBarEnabledKey = booleanPreferencesKey("liquidGlassNavBarEnabled")
val UseFloatingNavBarKey = booleanPreferencesKey("useFloatingNavBar")
val SavedAccountsKey = stringPreferencesKey("savedAccounts")
