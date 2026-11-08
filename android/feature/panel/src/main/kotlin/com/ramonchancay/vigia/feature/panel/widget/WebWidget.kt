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
        // Load once the view has its size: embeds like Spotify's pick their layout from the height at load time.
        factory = { context -> createWebView(context, host).apply { doOnLayout { loadUrl(url) } } },
        update = { view -> if (view.url != url && view.originalUrl != url) view.loadUrl(url) },
        onRelease = { it.destroy() }
    )
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
