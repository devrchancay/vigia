package com.ramonchancay.vigia.feature.panel.widget

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import java.net.URI

internal const val WEB_TAG = "web_widget"

/** Permissions player embeds ask their iframe for (playback, DRM, fullscreen). */
private const val IFRAME_ALLOW = "autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"

/**
 * An embedded page filling the tile (for example a Spotify playlist embed). Navigation stays on the page's
 * own host: links to other sites are ignored, so nobody can browse away from the panel.
 */
@Composable
internal fun WebWidget(widget: WidgetConfig, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.web(widget) }
    val url = params.value?.url
    if (url == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val host = remember(url) { URI(url).host }
    AndroidView(
        modifier = modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).testTag(WEB_TAG),
        // Load only once the view has its size: a page loaded at height 0 keeps a 0-height viewport (100vh = 0),
        // and embeds such as Spotify's then fall back to their compact layout or render nothing.
        factory = { context -> createWebView(context, host).apply { doOnLayout { load(url, host) } } },
        update = { view -> if (view.isLaidOut && view.tag != url) view.load(url, host) },
        onRelease = { it.destroy() }
    )
}

/**
 * Player embeds (Spotify, YouTube: URLs with `/embed/`) are made to live inside an iframe and fall back to a
 * compact layout when opened as the top page, so they get a full-size iframe; other pages load directly, since
 * many sites refuse to be framed.
 */
private fun WebView.load(url: String, host: String) {
    tag = url
    if ("/embed/" in url) {
        val src = url.replace("\"", "%22")
        // Height comes from innerHeight in script: WebView can keep 100vh at 0 when the viewport was computed
        // before the view had a size, which leaves the iframe invisible.
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;background:transparent;overflow:hidden}iframe{display:block;border:0;width:100%}</style>
            </head><body><iframe id="f" src="$src" allow="$IFRAME_ALLOW"></iframe>
            <script>const f=document.getElementById('f');const fit=()=>{f.style.height=innerHeight+'px'};
            addEventListener('resize',fit);fit();</script></body></html>"""
        loadDataWithBaseURL("https://$host/", html, "text/html", "utf-8", null)
    } else {
        loadUrl(url)
    }
}

// Embeds such as Spotify's player need JavaScript and DOM storage; content is limited to the configured host.
@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: android.content.Context, host: String): WebView = WebView(context).apply {
    setBackgroundColor(Color.TRANSPARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.url.host != host
    }
}
