package com.shiny.music.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.shiny.music.R
import com.shiny.music.constants.AccountChannelHandleKey
import com.shiny.music.constants.AccountEmailKey
import com.shiny.music.constants.AccountNameKey
import com.shiny.music.constants.DataSyncIdKey
import com.shiny.music.constants.InnerTubeCookieKey
import com.shiny.music.constants.SavedAccountsKey
import com.shiny.music.constants.VisitorDataKey
import com.shiny.music.models.AccountData
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.utils.rememberPreference
import com.shiny.music.utils.reportException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private val YouTubeRed = Color(0xFFFF0033)

/**
 * YouTube Music's own sign-in link. `service=youtube` and the hop through youtube.com/signin are
 * what give YouTube its session cookies: the bare `ServiceLogin?continue=music.youtube.com` signed
 * the user in to Google only and landed on YouTube Music still signed out (phone, 2026-09-30).
 */
private const val YouTubeSignInUrl =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
        "%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

/**
 * Signing in to YouTube Music: Google's own page, full screen, under a header that says
 * where you are and a hairline of progress. When Google hands back to YouTube Music the
 * account is read, remembered among the accounts on this phone, and Shiny restarts into it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val colors = Liquid.colors
    val scope = rememberCoroutineScope()

    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var savedAccountsJson by rememberPreference(SavedAccountsKey, "[]")

    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf("accounts.google.com") }
    var finishing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var hasCompletedLogin by remember { mutableStateOf(false) }
    // The account id every YouTube Music page reports, kept here until the sign-in is confirmed:
    // saved on its own (the page loads before Google signs anyone in) it named a user with no
    // credentials, and YouTube answered every song with 401.
    var pageDataSyncId by remember { mutableStateOf("") }

    fun finish(url: String) {
        // YouTube Music without its sign-in cookie means Google has not signed anyone in yet
        // (or the page failed to load): stay on the page. getCookie is null when there are no
        // cookies at all, and assigning that to the preference crashed the app.
        val cookie = CookieManager.getInstance().getCookie(url)
        if (cookie == null || "SAPISID=" !in cookie) {
            Timber.d("Login: reached YouTube Music without a signed-in cookie, staying on the page")
            return
        }
        hasCompletedLogin = true
        finishing = true
        failed = false
        scope.launch {
            delay(500)
            // Tried in memory first and saved only once YouTube accepts it, so a sign-in that
            // fails here leaves the previous account (or none) exactly as it was.
            val previousCookie = YouTube.cookie
            val previousDataSyncId = YouTube.dataSyncId
            YouTube.cookie = cookie
            YouTube.dataSyncId = pageDataSyncId
            YouTube.visitorData = visitorData
            YouTube.accountInfo().onSuccess { info ->
                innerTubeCookie = cookie
                dataSyncId = pageDataSyncId
                accountName = info.name
                accountEmail = info.email.orEmpty()
                accountChannelHandle = info.channelHandle.orEmpty()
                val account = AccountData(
                    name = info.name,
                    email = info.email.orEmpty(),
                    channelHandle = info.channelHandle.orEmpty(),
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = pageDataSyncId,
                    avatarUrl = info.thumbnailUrl.orEmpty(),
                )
                val accounts = runCatching { Json.decodeFromString<List<AccountData>>(savedAccountsJson) }
                    .getOrDefault(emptyList())
                    .filterNot { it.name == account.name } + account
                savedAccountsJson = Json.encodeToString(accounts)
                Timber.d("Login: signed in as ${info.name}, restarting")
                webView?.apply {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    clearFormData()
                }
                val restart = context.packageManager.getLaunchIntentForPackage(context.packageName)
                restart?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(restart)
                delay(500)
                Runtime.getRuntime().exit(0)
            }.onFailure { error ->
                Timber.e(error, "Login: account check failed")
                reportException(error)
                YouTube.cookie = previousCookie
                YouTube.dataSyncId = previousDataSyncId
                hasCompletedLogin = false
                finishing = false
                failed = true
            }
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true && !finishing) { webView?.goBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.Rounded.ChevronLeft,
                    iconSize = 28.dp,
                    onClick = { navController.navigateUp() },
                    contentDescription = stringResource(R.string.close),
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(YouTubeRed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.play), null, tint = Color.White, modifier = Modifier.size(11.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.login_youtube_title), style = LiquidTypography.headline, color = colors.label)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                        Icon(Icons.Rounded.Lock, null, tint = colors.secondaryLabel, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(host, style = LiquidTypography.caption1, color = colors.secondaryLabel, maxLines = 1)
                    }
                }
                GlassIconButton(
                    icon = Icons.Rounded.Refresh,
                    onClick = { webView?.reload() },
                    contentDescription = stringResource(R.string.refresh),
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (progress in 1..99) {
                    Box(Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(colors.accent))
                }
            }
            Text(
                text = stringResource(R.string.login_youtube_intro),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
                factory = { webContext ->
                    WebView(webContext).apply {
                        setBackgroundColor(if (colors.isDark) AndroidColor.BLACK else AndroidColor.WHITE)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull()?.let { h -> host = h } }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                                loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")
                                if (url == null || hasCompletedLogin) return
                                val pageHost = android.net.Uri.parse(url).host.orEmpty()
                                if (url.startsWith("https://music.youtube.com")) {
                                    finish(url)
                                } else if (
                                    (pageHost == "youtube.com" || pageHost.endsWith(".youtube.com")) &&
                                    CookieManager.getInstance().getCookie(url)?.contains("SAPISID=") == true
                                ) {
                                    // YouTube's sign-in hop sends a phone browser to m.youtube.com and
                                    // forgets YouTube Music (phone, 2026-09-30). The session is already
                                    // set for all of youtube.com, so carry on to YouTube Music.
                                    view.loadUrl("https://music.youtube.com/")
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onRetrieveVisitorData(newVisitorData: String?) {
                                if (newVisitorData != null) visitorData = newVisitorData
                            }

                            @JavascriptInterface
                            fun onRetrieveDataSyncId(newDataSyncId: String?) {
                                if (newDataSyncId != null) pageDataSyncId = newDataSyncId.substringBefore("||")
                            }
                        }, "Android")
                        webView = this
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        // Clearing is asynchronous: start the sign-in only once it is done, or it
                        // can wipe the cookies Google sets on its first page.
                        CookieManager.getInstance().removeAllCookies {
                            CookieManager.getInstance().flush()
                            loadUrl(YouTubeSignInUrl)
                        }
                    }
                },
            )
        }

        // Google has handed back: reading the account, then a restart into it.
        AnimatedVisibility(visible = finishing || failed, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.padding(horizontal = 40.dp).navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (finishing) {
                        ActivityIndicator(size = 34.dp)
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(R.string.login_finishing), style = LiquidTypography.title3, color = colors.label)
                    } else {
                        Text(
                            stringResource(R.string.login_youtube_failed),
                            style = LiquidTypography.body,
                            color = colors.label,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        LiquidButton(
                            text = stringResource(R.string.login_retry),
                            tone = ButtonTone.Filled,
                            onClick = {
                                failed = false
                                webView?.loadUrl(YouTubeSignInUrl)
                            },
                        )
                    }
                }
            }
        }
    }
}
