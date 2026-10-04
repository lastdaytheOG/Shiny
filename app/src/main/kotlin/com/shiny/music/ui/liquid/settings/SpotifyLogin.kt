package com.shiny.music.ui.liquid.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shiny.music.R
import com.shiny.music.spotify.SpotifyAuth
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidSearchField
import com.shiny.music.ui.liquid.LiquidTypography

val SpotifyGreen = Color(0xFF1ED760)

/** Sign-in pages that refuse to open inside an app, and which one it was. */
private enum class BlockedProvider { Google, Facebook }

private fun blockedProvider(url: String?): BlockedProvider? {
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return null
    return when {
        host == "accounts.google.com" || host.endsWith(".accounts.google.com") -> BlockedProvider.Google
        host == "facebook.com" || host.endsWith(".facebook.com") -> BlockedProvider.Facebook
        else -> null
    }
}

/**
 * Logging in to Spotify, in Spotify's own page, full screen.
 *
 * Google and Facebook refuse to show their sign-in inside another app ("This browser or
 * app may not be secure") — deliberately, so an app can never see the password — and no
 * honest app can change that. So those two buttons are caught before they go anywhere and
 * turned into what does work for those accounts: Spotify emails a login code to the
 * address the account was made with, or you set a Spotify password once. As a last resort
 * the session cookie can be pasted from a browser where you're already logged in.
 */
@Composable
fun SpotifyLoginPage(
    onDismiss: () -> Unit,
    onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
) {
    val colors = Liquid.colors
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf("accounts.spotify.com") }
    var blocked by remember { mutableStateOf<BlockedProvider?>(null) }
    var showCookie by remember { mutableStateOf(false) }
    var cookie by remember { mutableStateOf("") }
    var captured by remember { mutableStateOf(false) }

    fun capture(url: String?): Boolean {
        if (captured) return true
        val cookies = readSpotifyCookies(CookieManager.getInstance(), url)
        val spDc = cookies["sp_dc"].orEmpty()
        if (spDc.isBlank()) return false
        captured = true
        CookieManager.getInstance().flush()
        onCookiesCaptured(spDc, cookies["sp_key"].orEmpty())
        return true
    }

    fun intercept(view: WebView, url: String?): Boolean {
        if (capture(url)) return true
        blockedProvider(url)?.let { provider ->
            blocked = provider
            return true
        }
        val target = url?.takeIf { it.isNotBlank() } ?: return false
        if (target.isWebViewLoadable()) return false
        target.intentBrowserFallbackUrl()?.let(view::loadUrl)
        return true
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }
    }
    BackHandler {
        when {
            blocked != null -> blocked = null
            showCookie -> showCookie = false
            webView?.canGoBack() == true -> webView?.goBack()
            else -> onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Header: close, who you're signing in to, reload.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(icon = Icons.Rounded.Close, onClick = onDismiss, contentDescription = stringResource(R.string.close))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(18.dp).clip(CircleShape).background(SpotifyGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ic_spotify), null, tint = Color.Black, modifier = Modifier.size(12.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.spotify_login_header), style = LiquidTypography.headline, color = colors.label)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                            Icon(Icons.Rounded.Lock, null, tint = colors.secondaryLabel, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(host, style = LiquidTypography.caption1, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    GlassIconButton(icon = Icons.Rounded.Refresh, onClick = { webView?.reload() }, contentDescription = stringResource(R.string.refresh))
                }
                // A hairline of progress, only while a page is loading.
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (progress in 1..99) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(2.dp)
                                .background(SpotifyGreen)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.spotify_login_tip),
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
                    factory = { ctx ->
                        val container = android.widget.FrameLayout(ctx)
                        val main = WebView(ctx).apply {
                            configureForSpotify()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    intercept(view, request.url?.toString())

                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                    url?.let { runCatching { Uri.parse(it).host }.getOrNull()?.let { h -> host = h } }
                                    if (blockedProvider(url) != null) {
                                        view.stopLoading()
                                        blocked = blockedProvider(url)
                                        if (view.canGoBack()) view.goBack() else view.loadUrl(SpotifyAuth.LOGIN_URL)
                                        return
                                    }
                                    capture(url)
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    capture(url)
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }

                                // "Continue with Google" opens a popup; it never gets one.
                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message,
                                ): Boolean {
                                    val popup = WebView(view.context).apply {
                                        configureForSpotify()
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                                                val url = request.url?.toString()
                                                if (blockedProvider(url) != null) {
                                                    blocked = blockedProvider(url)
                                                    v.destroy()
                                                    return true
                                                }
                                                if (capture(url)) return true
                                                // Anything else a popup wants belongs in the main page.
                                                url?.takeIf { it.isWebViewLoadable() }?.let(view::loadUrl)
                                                v.destroy()
                                                return true
                                            }

                                            override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                                if (blockedProvider(url) != null) {
                                                    v.stopLoading()
                                                    blocked = blockedProvider(url)
                                                }
                                            }
                                        }
                                    }
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            val cookies = CookieManager.getInstance()
                            cookies.setAcceptCookie(true)
                            cookies.setAcceptThirdPartyCookies(this, true)
                            cookies.removeAllCookies(null)
                            cookies.flush()
                            loadUrl(SpotifyAuth.LOGIN_URL)
                        }
                        webView = main
                        container.addView(
                            main,
                            android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        container
                    },
                )
            }

            // Why Google (or Facebook) didn't open, and what to do instead.
            AnimatedVisibility(
                visible = blocked != null || showCookie,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
            AnimatedVisibility(
                visible = blocked != null || showCookie,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(if (colors.isDark) Color(0xFF1C1C1E) else Color.White)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showCookie) {
                        Text(stringResource(R.string.spotify_cookie_title), style = LiquidTypography.title3, color = colors.label)
                        Text(stringResource(R.string.spotify_cookie_text), style = LiquidTypography.subheadline, color = colors.secondaryLabel)
                        LiquidSearchField(
                            value = cookie,
                            onValueChange = { cookie = it.trim().removePrefix("sp_dc=") },
                            placeholder = "sp_dc",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LiquidButton(
                            text = stringResource(R.string.spotify_cookie_connect),
                            tone = ButtonTone.Filled,
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black,
                            enabled = cookie.length > 20,
                            height = 50.dp,
                            onClick = {
                                captured = true
                                onCookiesCaptured(cookie, "")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LiquidButton(
                            text = stringResource(android.R.string.cancel),
                            height = 46.dp,
                            onClick = { showCookie = false },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        val provider = if (blocked == BlockedProvider.Facebook) "Facebook" else "Google"
                        Text(stringResource(R.string.spotify_blocked_title, provider), style = LiquidTypography.title3, color = colors.label)
                        Text(stringResource(R.string.spotify_blocked_text, provider), style = LiquidTypography.subheadline, color = colors.secondaryLabel)
                        Spacer(Modifier.height(4.dp))
                        LiquidButton(
                            text = stringResource(R.string.spotify_blocked_code),
                            tone = ButtonTone.Filled,
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black,
                            height = 50.dp,
                            onClick = {
                                blocked = null
                                webView?.loadUrl(SpotifyAuth.LOGIN_URL)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LiquidButton(
                            text = stringResource(R.string.spotify_blocked_password),
                            height = 46.dp,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.spotify.com/password-reset/"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LiquidButton(
                            text = stringResource(R.string.spotify_blocked_cookie),
                            height = 46.dp,
                            onClick = {
                                blocked = null
                                showCookie = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Spotify renders its full login (email, phone, code) for a desktop browser. */
private const val SpotifyUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForSpotify() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        userAgentString = SpotifyUserAgent
    }
}

private fun String.isWebViewLoadable(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https" || scheme == "javascript" || scheme == "data" || scheme == "blob"
}

private fun String.intentBrowserFallbackUrl(): String? =
    runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }
        .getOrNull()
        ?.getStringExtra("browser_fallback_url")
        ?.takeIf { it.isWebViewLoadable() }

private fun readSpotifyCookies(cookieManager: CookieManager, currentUrl: String?): Map<String, String> {
    val urls = linkedSetOf("https://open.spotify.com", "https://accounts.spotify.com", "https://spotify.com")
    currentUrl?.let { url ->
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val h = uri?.host?.lowercase()
        if (h != null && (h == "spotify.com" || h.endsWith(".spotify.com"))) urls += "https://$h"
    }
    val cookies = linkedMapOf<String, String>()
    cookieManager.flush()
    urls.forEach { url ->
        cookieManager.getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@forEach
                val key = part.substring(0, separator).trim()
                if (key.isNotBlank()) cookies[key] = part.substring(separator + 1).trim()
            }
    }
    return cookies
}
