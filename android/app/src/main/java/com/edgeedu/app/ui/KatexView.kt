package com.edgeedu.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/**
 * Renders LaTeX via the bundled KaTeX distribution (assets/katex/) in a small
 * WebView. Fully offline: assets are served through WebViewAssetLoader from
 * inside the APK; JavaScript is enabled only for KaTeX itself and no remote
 * URL is ever requested.
 *
 * The view measures the rendered formula and grows to fit it (within a sane
 * cap), so multi-line working and long answers are never clipped — the message
 * box expands with the content instead of cutting it off.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KatexView(latex: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // The page reports its rendered pixel height after KaTeX lays out; until
    // then use a one-line default. Clamp so nothing collapses or runs away.
    var heightPx by remember { mutableStateOf(0) }
    val height = (if (heightPx > 0) with(density) { heightPx.toDp() } else 44.dp)
        .coerceIn(36.dp, 600.dp)

    AndroidView(
        modifier = modifier.height(height),
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(0x00000000)
                // Lets the page report its rendered height back to Compose.
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun report(px: Int) { post { heightPx = px } }
                }, "AndroidSizer")
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                }
            }
        },
        update = { webView ->
            val escaped = latex
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")
            val html = """
                <!DOCTYPE html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://appassets.androidplatform.net/assets/katex/katex.min.css">
                <script src="https://appassets.androidplatform.net/assets/katex/katex.min.js"></script>
                <style>
                  body { margin: 4px; font-size: 18px; background: transparent; }
                  #m { overflow-x: auto; }
                </style>
                </head><body><div id="m"></div>
                <script>
                  katex.render(`$escaped`, document.getElementById('m'),
                               { throwOnError: false, displayMode: true });
                  function reportSize() {
                    var h = document.body.scrollHeight;
                    if (window.AndroidSizer) AndroidSizer.report(Math.ceil(h * window.devicePixelRatio));
                  }
                  // Report after layout settles (fonts/CSS applied).
                  window.requestAnimationFrame(function(){ setTimeout(reportSize, 30); });
                </script>
                </body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/assets/",
                html, "text/html", "utf-8", null
            )
        }
    )
}
