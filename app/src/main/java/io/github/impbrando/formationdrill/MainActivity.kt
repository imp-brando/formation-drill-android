package io.github.impbrando.formationdrill

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import androidx.webkit.WebViewAssetLoader

/**
 * Thin native shell: a full-screen WebView running the game from GitHub Pages (edits go live with no
 * app update; the page's service worker keeps an offline copy), with a bundled copy as a fallback for
 * a first launch with no signal. Voice comes from [VoiceEngine] through the FGAndroid bridge.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var voice: VoiceEngine
    private var pendingStart = false
    private var usingFallback = false
    @Volatile private var trusted = false

    private val fallbackUrl = "https://appassets.androidplatform.net/assets/www/index.html"
    private val trustedHosts = setOf(Uri.parse(BuildConfig.GAME_URL).host, "appassets.androidplatform.net")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        setContentView(web)
        voice = VoiceEngine(applicationContext) { js -> runOnUiThread { if (trusted) web.evaluateJavascript("window.FGN&&$js", null) } }
        voice.loadModel()

        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
        }
        web.setBackgroundColor(0xFF3B4220.toInt())
        web.addJavascriptInterface(Bridge(), "FGAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest) = loader.shouldInterceptRequest(req.url)
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                trusted = Uri.parse(url).host in trustedHosts
            }
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                if (req.url.host in trustedHosts) return false
                startActivity(Intent(Intent.ACTION_VIEW, req.url)); return true      // other links: system browser
            }
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                // no signal and nothing cached yet: use the copy bundled in the app
                if (req.isForMainFrame && !usingFallback) { usingFallback = true; view.loadUrl(fallbackUrl) }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {     // browser-mic fallback path
                val ok = Uri.parse(request.origin.toString()).host in trustedHosts && hasMic()
                if (ok) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else request.deny()
            }
        }
        if (savedInstanceState == null) web.loadUrl(BuildConfig.GAME_URL) else web.restoreState(savedInstanceState)
        immersive()
    }

    private fun immersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startVoice() {
        if (hasMic()) voice.start()
        else { pendingStart = true; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (code != 1) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) { if (pendingStart) voice.start() }
        else web.evaluateJavascript("window.FGN&&FGN.state('denied')", null)
        pendingStart = false
    }

    /** Called from the game's JavaScript (background thread). Only answers pages from our own hosts. */
    inner class Bridge {
        @JavascriptInterface fun start() { if (trusted) runOnUiThread { startVoice() } }
        @JavascriptInterface fun stop() { if (trusted) voice.stop() }
        @JavascriptInterface fun setGrammar(json: String) { if (trusted) voice.setGrammar(json) }
        @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out) }
    override fun onPause() { super.onPause(); voice.stop(); web.onPause() }          // never listen in the background
    override fun onResume() { super.onResume(); web.onResume(); immersive() }
    override fun onDestroy() { voice.release(); web.destroy(); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
