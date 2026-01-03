package com.example.host_mini_app_telegram

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.integration.android.IntentIntegrator
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var toolbar: Toolbar
    private lateinit var btnBack: Button
    private lateinit var btnMain: Button
    private lateinit var btnSettings: Button
    private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, result.resultCode, result.data)
        if (intentResult != null) {
            if (intentResult.contents != null) {
                val scannedText = intentResult.contents
                webView.evaluateJavascript("if(window.onAndroidQrScanned) { window.onAndroidQrScanned('$scannedText'); }", null)
            } else {
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('scan_qr_popup_closed'));", null)
            }
        }
    }
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startQrScan()
        } else {
            Toast.makeText(this, "Cần cấp quyền Camera để quét QR", Toast.LENGTH_SHORT).show()
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webView)
        btnBack = findViewById(R.id.btnBack)
        btnMain = findViewById(R.id.btnMain)
        btnSettings = findViewById(R.id.btnSettings)

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        setupWebView()
        btnMain.visibility = android.view.View.GONE
        btnSettings.visibility = android.view.View.GONE
        btnBack.visibility = android.view.View.GONE
        btnBack.setOnClickListener {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('back_button_pressed'));", null)
        }

        btnMain.setOnClickListener {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('main_button_pressed'));", null)
        }
        btnSettings.setOnClickListener {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('settings_button_pressed'));", null)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            setSupportZoom(true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Mini App Alert")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                syncTheme()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("file://") || url.contains("localhost")) {
                    return false
                }
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Không thể mở link: $url", Toast.LENGTH_SHORT).show()
                }

                return true
            }
        }
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        val userId = "999999"
        val firstName = "Hoàng"
        val username = "Hoàng Hữu Tín"

        val assetUrl = "file:///android_asset/dist/index.html"
        val fullUrl = "$assetUrl?user_id=$userId&first_name=$firstName&username=$username"

        webView.loadUrl(fullUrl)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncTheme()
    }

    private fun getThemeParams(): String {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bg_color = if (isDarkMode) "#17212b" else "#ffffff"
        val text_color = if (isDarkMode) "#f5f5f5" else "#000000"
        val hint_color = if (isDarkMode) "#708499" else "#999999"
        val button_color = if (isDarkMode) "#5288c1" else "#3390ec"
        val button_text_color = "#ffffff"
        val secondary_bg_color = if (isDarkMode) "#232e3c" else "#f0f2f5"
        val header_bg_color = if (isDarkMode) "#17212b" else "#ffffff"
        val accent_text_color = if (isDarkMode) "#6ab2f2" else "#168acd"

        return "{\"bg_color\":\"$bg_color\",\"text_color\":\"$text_color\",\"hint_color\":\"$hint_color\",\"link_color\":\"$accent_text_color\",\"button_color\":\"$button_color\",\"button_text_color\":\"$button_text_color\",\"secondary_bg_color\":\"$secondary_bg_color\",\"header_bg_color\":\"$header_bg_color\",\"accent_text_color\":\"$accent_text_color\",\"section_bg_color\":\"$header_bg_color\",\"section_header_text_color\":\"$accent_text_color\",\"subtitle_text_color\":\"$hint_color\",\"destructive_text_color\":\"#ff3b30\"}"
    }
    fun syncTheme() {
        val json = getThemeParams()
        webView.evaluateJavascript("if(window.updateTheme) { window.updateTheme('$json'); }", null)
    }
    fun checkAndStartQrScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startQrScan()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    private fun startQrScan() {
        val integrator = IntentIntegrator(this)
        integrator.setPrompt("Quét mã QR")
        integrator.setOrientationLocked(false)
        integrator.setBeepEnabled(false)
        val intent = integrator.createScanIntent()
        qrScanLauncher.launch(intent)
    }
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("Android")
        webView.loadUrl("about:blank")
        super.onDestroy()
    }
    fun setBackButtonVisible(isVisible: Boolean) {
        runOnUiThread {
            btnBack.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    fun updateHeaderColor(colorKeyOrHex: String) {
        var color = android.graphics.Color.parseColor("#ffffff")

        try {
            if (colorKeyOrHex.startsWith("#")) {
                color = android.graphics.Color.parseColor(colorKeyOrHex)
            }
            else {
                val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (colorKeyOrHex == "secondary_bg_color") {
                    color = android.graphics.Color.parseColor(if (isDarkMode) "#232e3c" else "#f0f2f5")
                } else {
                    color = android.graphics.Color.parseColor(if (isDarkMode) "#17212b" else "#ffffff")
                }
            }

            toolbar.setBackgroundColor(color)

            window.statusBarColor = color

            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = !isColorDark(color)

        } catch (e: Exception) {
            // Ignore error
        }
    }


    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color) + 0.114 * android.graphics.Color.blue(color)) / 255
        return darkness >= 0.5
    }
}


class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(100)
        }
    }

    @JavascriptInterface
    fun closeApp() {
        if (context is Activity) {
            context.finish()
        }
    }

    @JavascriptInterface
    fun setMainButtonText(text: String) {
        if (context is Activity) {
            context.runOnUiThread {
                val btnMain = context.findViewById<Button>(R.id.btnMain)
                btnMain.text = text
            }
        }
    }

    @JavascriptInterface
    fun setMainButtonVisible(isVisible: Boolean) {
        if (context is Activity) {
            context.runOnUiThread {
                val btnMain = context.findViewById<Button>(R.id.btnMain)
                btnMain.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    @JavascriptInterface
    fun setMainButtonColor(color: String) {
        if (context is Activity) {
            context.runOnUiThread {
                val btnMain = context.findViewById<Button>(R.id.btnMain)
                try {
                    btnMain.setBackgroundColor(android.graphics.Color.parseColor(color))
                } catch (e: Exception) {
                    // Ignore color parse error
                }
            }
        }
    }

    @JavascriptInterface
    fun openPopup(title: String, message: String, buttonsJson: String) {
        if (context is Activity) {
            context.runOnUiThread {
                val builder = AlertDialog.Builder(context)
                builder.setTitle(title)
                builder.setMessage(message)
                builder.setCancelable(false)
                try {
                    val buttons = org.json.JSONArray(buttonsJson)

                    for (i in 0 until buttons.length()) {
                        val btn = buttons.getJSONObject(i)
                        val id = btn.optString("id", "")
                        val type = btn.optString("type", "default")
                        var text = btn.optString("text", "")

                        if (text.isEmpty()) {
                            text = when (type) {
                                "ok" -> "Đồng ý"
                                "cancel" -> "Hủy"
                                "destructive" -> "Xóa"
                                else -> "OK"
                            }
                        }

                        val listener = { _: android.content.DialogInterface, _: Int -> sendPopupEvent(id) }

                        when (i) {
                            0 -> builder.setPositiveButton(text, listener)
                            1 -> builder.setNegativeButton(text, listener)
                            2 -> builder.setNeutralButton(text, listener)
                        }
                    }

                } catch (e: Exception) {
                    builder.setPositiveButton("OK (Fallback)") { _, _ -> sendPopupEvent("cancel") }
                }

                builder.show()
            }
        }
    }

    @JavascriptInterface
    fun hapticFeedback(type: String, style: String) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (type) {
                "impact" -> {
                    when (style) {
                        "light" -> vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                        "medium" -> vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                        "heavy" -> vibrator.vibrate(VibrationEffect.createOneShot(80, 255))
                        else -> vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
                "notification" -> {
                    when (style) {
                        "success" -> {
                            val timing = longArrayOf(0, 50, 50, 100)
                            val amplitudes = intArrayOf(0, 100, 0, 200)
                            vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitudes, -1))
                        }
                        "warning" -> {
                            val timing = longArrayOf(0, 50, 100, 200)
                            val amplitudes = intArrayOf(0, 150, 0, 150)
                            vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitudes, -1))
                        }
                        "error" -> {
                            val timing = longArrayOf(0, 50, 50, 50, 50, 100)
                            val amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
                            vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitudes, -1))
                        }
                    }
                }
                "selection_change" -> {
                    vibrator.vibrate(VibrationEffect.createOneShot(10, 50))
                }
            }
        } else {
            vibrator.vibrate(50)
        }
    }
    @JavascriptInterface
    fun requestTheme() {
        if (context is Activity) {
            context.runOnUiThread {
                (context as? MainActivity)?.syncTheme()
            }
        }
    }
    @JavascriptInterface
    fun openLink(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            if (context is Activity) {
                context.runOnUiThread {
                    Toast.makeText(context, "Cannot open link: $url", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @JavascriptInterface
    fun openTelegramLink(url: String) {
        openLink(url)
    }
    private fun sendPopupEvent(buttonId: String) {
        if (context is Activity) {
            context.runOnUiThread {
                val webView = context.findViewById<WebView>(R.id.webView)
                val js = "if (window.onAndroidPopupClosed) { window.onAndroidPopupClosed('$buttonId'); }"
                webView.evaluateJavascript(js, null)
            }
        }
    }
    @JavascriptInterface
    fun scanQrCode() {
        if (context is MainActivity) {
            context.runOnUiThread {
                context.checkAndStartQrScan()
            }
        }
    }

    @JavascriptInterface
    fun setBackButtonVisible(isVisible: Boolean) {
        if (context is Activity) {
            context.runOnUiThread {
                val btnBack = context.findViewById<Button>(R.id.btnBack)
                btnBack.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }
    @JavascriptInterface
    fun setHeaderColor(colorKeyOrHex: String) {
        if (context is MainActivity) {
            context.runOnUiThread {
                context.updateHeaderColor(colorKeyOrHex)
            }
        }
    }
    @JavascriptInterface
    fun setSettingsButtonVisible(isVisible: Boolean) {
        if (context is Activity) {
            context.runOnUiThread {
                val btnSettings = context.findViewById<Button>(R.id.btnSettings)
                btnSettings.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }
}