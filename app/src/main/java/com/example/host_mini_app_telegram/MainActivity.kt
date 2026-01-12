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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var toolbar: Toolbar
    private lateinit var btnBack: Button
    private lateinit var btnMain: Button
    private lateinit var btnSecondary: Button // Thêm biến này
    private lateinit var btnSettings: Button
    private var needCloseConfirmation = false
    private var isExpanded = false
    private var allowSwipeClose = true
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
            // Chỉ apply padding khi KHÔNG expanded
            if (!isExpanded) {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webView)
        btnBack = findViewById(R.id.btnBack)
        btnMain = findViewById(R.id.btnMain)
        // Ánh xạ Button Secondary (Lưu ý: phải khớp ID trong activity_main.xml)
        btnSecondary = findViewById(R.id.btnSecondary)
        btnSettings = findViewById(R.id.btnSettings)

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        setupWebView()

        // Cấu hình ban đầu cho các nút
        btnMain.visibility = android.view.View.GONE
        btnSecondary.visibility = android.view.View.GONE // Mặc định ẩn
        btnSettings.visibility = android.view.View.GONE
        btnBack.visibility = android.view.View.GONE

        // --- CÁC SỰ KIỆN CLICK BUTTON ---
        btnBack.setOnClickListener {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('back_button_pressed'));", null)
        }

        btnMain.setOnClickListener {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('main_button_pressed'));", null)
        }

        // Thêm sự kiện click cho Secondary Button (Feature SecondaryButton SDK)
        btnSecondary.setOnClickListener {
            webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('secondary_button_pressed'));", null)
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
        webView.addJavascriptInterface(WebAppInterface(this), "TelegramWebviewProxy")

        val userId = intent.getStringExtra("user_id") ?: "999999"
        val firstName = intent.getStringExtra("first_name") ?: "Hoàng"
        val username = intent.getStringExtra("username") ?: "Hoàng Hữu Tín"
        val startParam = intent.getStringExtra("start_param") ?: ""
        val assetUrl = "file:///android_asset/dist/index.html"
        val launchParams = buildLaunchParams()
        val fullUrl = "$assetUrl#/?$launchParams"
        android.util.Log.d("MainActivity", "Loading URL: $fullUrl")
        webView.loadUrl(fullUrl)
    }
    private fun buildLaunchParams(): String {
        val themeParams = getThemeParamsJson()
        return "tgWebAppVersion=8.4" +
                "&tgWebAppPlatform=android" +
                "&tgWebAppThemeParams=${URLEncoder.encode(themeParams, "UTF-8")}"
    }
    private fun getThemeParamsJson(): String {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg_color = if (isDarkMode) "#17212b" else "#ffffff"
        val text_color = if (isDarkMode) "#f5f5f5" else "#000000"
        val hint_color = if (isDarkMode) "#708499" else "#999999"
        val button_color = if (isDarkMode) "#5288c1" else "#3390ec"
        val button_text_color = "#ffffff"
        val secondary_bg_color = if (isDarkMode) "#232e3c" else "#f0f2f5"
        val header_bg_color = if (isDarkMode) "#17212b" else "#ffffff"
        val accent_text_color = if (isDarkMode) "#6ab2f2" else "#168acd"
        return """{"bg_color":"$bg_color","text_color":"$text_color","hint_color":"$hint_color","link_color":"$accent_text_color","button_color":"$button_color","button_text_color":"$button_text_color","secondary_bg_color":"$secondary_bg_color","header_bg_color":"$header_bg_color","accent_text_color":"$accent_text_color","section_bg_color":"$header_bg_color","section_header_text_color":"$accent_text_color","subtitle_text_color":"$hint_color","destructive_text_color":"#ff3b30"}"""
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
        // Nếu không cho phép swipe close VÀ đang ở trang đầu tiên
        if (!allowSwipeClose && !webView.canGoBack()) {
            Toast.makeText(this, "Swipe to close đã bị vô hiệu hóa", Toast.LENGTH_SHORT).show()
            return // Chặn không cho đóng
        }

        if (needCloseConfirmation) {
            AlertDialog.Builder(this)
                .setTitle("Xác nhận thoát")
                .setMessage("Bạn có chắc muốn thoát Mini App?")
                .setPositiveButton("Thoát") { _, _ ->
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        super.onBackPressed()
                    }
                }
                .setNegativeButton("Ở lại", null)
                .show()
        } else {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("TelegramWebviewProxy")
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

            // Hỗ trợ set icon status bar sáng/tối
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
    fun setNeedCloseConfirmation(need: Boolean) {
        needCloseConfirmation = need
    }
    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
    }

    // Feature: Swipe Behavior
    fun setSwipeEnabled(enabled: Boolean) {
        runOnUiThread {
            allowSwipeClose = enabled
            Toast.makeText(this, "Swipe to close: ${if (enabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
    }

    // Feature: Invoice
    fun openInvoice(slug: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Invoice Payment")
                .setMessage("Thanh toán Invoice: $slug\n(Mô phỏng thanh toán $10)")
                .setPositiveButton("Thanh toán") { _, _ ->
                    // Gửi event invoice_closed trạng thái paid về mini app
                    webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('invoice_closed', {detail: {slug: '$slug', status: 'paid'}}));", null)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    // Feature: Fullscreen
    fun setFullscreen(fullscreen: Boolean) {
        runOnUiThread {
            val params = window.attributes
            if (fullscreen) {
                // Ẩn status bar và toolbar
                // window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) // Deprecated on new Android but works

                // Ẩn System Bars (Status & Nav)
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

                supportActionBar?.hide()
                findViewById<Toolbar>(R.id.toolbar).visibility = android.view.View.GONE
            } else {
                // Hiện lại
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())

                supportActionBar?.show()
                findViewById<Toolbar>(R.id.toolbar).visibility = android.view.View.VISIBLE
            }
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sdk_test -> {
                showSDKTestMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun showSDKTestMenu() {
        val anchor = findViewById<Toolbar>(R.id.toolbar)
        val popupMenu = PopupMenu(this, anchor)

        // === 1. BUTTONS - Các nút bấm ===
        popupMenu.menu.add(0, 1, 0, "🚀 Hiện Nút Chính")
        popupMenu.menu.add(0, 2, 0, "🚀 Ẩn Nút Chính")
        popupMenu.menu.add(0, 3, 0, "📝 Đổi tên: Thanh Toán")
        popupMenu.menu.add(0, 4, 0, "🎨 Đổi màu Hồng")
        popupMenu.menu.add(0, 5, 0, "✅ Enable")
        popupMenu.menu.add(0, 6, 0, "🚫 Disable")
        popupMenu.menu.add(0, 7, 0, "⏳ Loading")
        popupMenu.menu.add(0, 8, 0, "✓ Done")
        popupMenu.menu.add(0, 9, 0, "🥈 Hiện Nút Phụ")
        popupMenu.menu.add(0, 10, 0, "🥈 Ẩn Nút Phụ")
        popupMenu.menu.add(0, 11, 0, "⬅️ Hiện Nút Back")
        popupMenu.menu.add(0, 12, 0, "⬅️ Ẩn Nút Back")
        popupMenu.menu.add(0, 13, 0, "⚙️ Hiện Nút Settings")
        popupMenu.menu.add(0, 14, 0, "⚙️ Ẩn Nút Settings")

        // === 2. UI CONTROL - Điều khiển giao diện ===
        popupMenu.menu.add(1, 20, 0, "🎨 Header Đỏ")
        popupMenu.menu.add(1, 21, 0, "🎨 Header Xanh")
        popupMenu.menu.add(1, 22, 0, "🎨 Header Theme")
        popupMenu.menu.add(1, 23, 0, "🎨 Bottom Bar Xanh Dương")
        popupMenu.menu.add(1, 24, 0, "🎨 Bottom Bar Cam")
        popupMenu.menu.add(1, 112, 0, "🎨 Bottom Bar Xanh Lá")
        popupMenu.menu.add(1, 113, 0, "🎨 Bottom Bar Tím")
        popupMenu.menu.add(1, 25, 0, "📐 Expand Full Screen")
        popupMenu.menu.add(1, 26, 0, "🖥️ Vào Fullscreen")
        popupMenu.menu.add(1, 27, 0, "🖥️ Thoát Fullscreen")
        popupMenu.menu.add(1, 28, 0, "👆 Tắt Swipe to Close")
        popupMenu.menu.add(1, 29, 0, "👆 Bật Swipe to Close")
        popupMenu.menu.add(1, 30, 0, "🔒 Bật Xác nhận đóng")
        popupMenu.menu.add(1, 31, 0, "🔓 Tắt Xác nhận đóng")

        // === 3. FEEDBACK - Phản hồi ===
        popupMenu.menu.add(2, 40, 0, "💬 Toast Hello")
        popupMenu.menu.add(2, 41, 0, "📳 Rung Nhẹ")
        popupMenu.menu.add(2, 42, 0, "📳 Rung Mạnh")
        popupMenu.menu.add(2, 43, 0, "✅ Rung Success")
        popupMenu.menu.add(2, 44, 0, "❌ Rung Error")
        popupMenu.menu.add(2, 45, 0, "👆 Rung Selection")
        popupMenu.menu.add(2, 46, 0, "💬 Hiện Popup Chuẩn")

        // === 4. ACTIONS - Hành động ===
        popupMenu.menu.add(3, 50, 0, "📷 Quét QRCode")
        popupMenu.menu.add(3, 51, 0, "💰 Mở Invoice")
        popupMenu.menu.add(3, 52, 0, "📢 Share App")
        popupMenu.menu.add(3, 53, 0, "📖 Share to Story")
        popupMenu.menu.add(3, 54, 0, "📥 Download")
        popupMenu.menu.add(3, 55, 0, "🖼️ Xem Media")
        popupMenu.menu.add(3, 56, 0, "🔗 Mở Link")
        popupMenu.menu.add(3, 57, 0, "📤 Gửi Data")
        popupMenu.menu.add(3, 58, 0, "🔍 Mở Inline Query")
        popupMenu.menu.add(3, 59, 0, "🚪 Đóng App")

        // === 5. PERMISSIONS - Quyền truy cập ===
        popupMenu.menu.add(4, 60, 0, "✍️ Yêu cầu quyền gửi tin")
        popupMenu.menu.add(4, 61, 0, "📞 Yêu cầu số điện thoại")
        popupMenu.menu.add(4, 62, 0, "📋 Đọc Clipboard")

        // === 6. STORAGE - Cloud Storage ===
        popupMenu.menu.add(5, 70, 0, "💾 Lưu (test_key)")
        popupMenu.menu.add(5, 71, 0, "📖 Đọc (test_key)")
        popupMenu.menu.add(5, 72, 0, "🗑️ Xóa (test_key)")
        popupMenu.menu.add(5, 73, 0, "🔑 Lấy Keys")

        // === 7. SENSORS - Cảm biến ===
        popupMenu.menu.add(6, 80, 0, "📱 Accelerometer Start")
        popupMenu.menu.add(6, 81, 0, "📱 Accelerometer Stop")
        popupMenu.menu.add(6, 82, 0, "🌀 Gyroscope Start")
        popupMenu.menu.add(6, 83, 0, "🌀 Gyroscope Stop")
        popupMenu.menu.add(6, 84, 0, "🧭 Device Orientation Start")
        popupMenu.menu.add(6, 85, 0, "🧭 Device Orientation Stop")

        // === 8. LOCATION - Vị trí ===
        popupMenu.menu.add(7, 90, 0, "📍 Lấy vị trí")
        popupMenu.menu.add(7, 91, 0, "⚙️ Cài đặt Location")

        // === 9. BIOMETRIC - Sinh trắc học ===
        popupMenu.menu.add(8, 100, 0, "🔍 Kiểm tra Biometric")
        popupMenu.menu.add(8, 101, 0, "🔐 Xác thực")
        popupMenu.menu.add(8, 102, 0, "⚙️ Settings Biometric")

        // === 10. OTHERS - Khác ===
        popupMenu.menu.add(9, 110, 0, "😀 Đặt Emoji Status")
        popupMenu.menu.add(9, 111, 0, "🏠 Thêm vào Home Screen")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            handleSDKMenuClick(menuItem.itemId)
            true
        }

        popupMenu.show()
    }

    private fun handleSDKMenuClick(itemId: Int) {
        val js = when (itemId) {
            // === 1. BUTTONS ===
            1 -> "if(window.Android) { window.Android.setMainButtonVisible(true); window.Android.setMainButtonText('Main Button'); }"
            2 -> "if(window.Android) { window.Android.setMainButtonVisible(false); }"
            3 -> "if(window.Android) { window.Android.setMainButtonText('💳 Thanh Toán'); }"
            4 -> "if(window.Android) { window.Android.setMainButtonColor('#ff69b4'); }"
            5 -> "if(window.Android) { window.Android.setMainButtonEnabled(true); }"
            6 -> "if(window.Android) { window.Android.setMainButtonEnabled(false); }"
            7 -> "if(window.Android) { window.Android.setMainButtonProgress(true); }"
            8 -> "if(window.Android) { window.Android.setMainButtonProgress(false); }"
            9 -> "if(window.Android) { window.Android.setSecondaryButtonVisible(true); window.Android.setSecondaryButtonText('Secondary'); }"
            10 -> "if(window.Android) { window.Android.setSecondaryButtonVisible(false); }"
            11 -> "if(window.Android) { window.Android.setBackButtonVisible(true); }"
            12 -> "if(window.Android) { window.Android.setBackButtonVisible(false); }"
            13 -> "if(window.Android) { window.Android.setSettingsButtonVisible(true); }"
            14 -> "if(window.Android) { window.Android.setSettingsButtonVisible(false); }"

            // === 2. UI CONTROL ===
            20 -> "if(window.Android) { window.Android.setHeaderColor('#ff0000'); }"
            21 -> "if(window.Android) { window.Android.setHeaderColor('#008000'); }"
            22 -> "if(window.Android) { window.Android.setHeaderColor('secondary_bg_color'); }"
            23 -> "if(window.Android) { window.Android.setBottomBarColor('#3390ec'); }"
            24 -> "if(window.Android) { window.Android.setBottomBarColor('#ff5722'); }"
            25 -> "if(window.Android) { window.Android.expandViewport(); }"
            26 -> "if(window.Android) { window.Android.setFullscreen(true); }"
            27 -> "if(window.Android) { window.Android.setFullscreen(false); }"
            28 -> "if(window.Android) { window.Android.setSwipeEnabled(false); }"
            29 -> "if(window.Android) { window.Android.setSwipeEnabled(true); }"
            30 -> "if(window.Android) { window.Android.setClosingConfirmation(true); }"
            31 -> "if(window.Android) { window.Android.setClosingConfirmation(false); }"

            // === 3. FEEDBACK ===
            40 -> "if(window.Android) { window.Android.showToast('Hello từ Menu!'); }"
            41 -> "if(window.Android) { window.Android.hapticFeedback('impact', 'light'); }"
            42 -> "if(window.Android) { window.Android.hapticFeedback('impact', 'heavy'); }"
            43 -> "if(window.Android) { window.Android.hapticFeedback('notification', 'success'); }"
            44 -> "if(window.Android) { window.Android.hapticFeedback('notification', 'error'); }"
            45 -> "if(window.Android) { window.Android.hapticFeedback('selection_change', ''); }"
            46 -> "if(window.Android) { window.Android.openPopup('Test', 'From Android Menu', '[{\"type\":\"ok\",\"text\":\"OK\"},{\"type\":\"cancel\",\"text\":\"Hủy\"}]'); }"

            // === 4. ACTIONS ===
            50 -> "if(window.Android) { window.Android.scanQrCode(); }"
            51 -> "if(window.Android) { window.Android.openInvoice('test-invoice-slug'); }"
            52 -> "if(window.Android) { window.Android.shareText('Hello from SDK Menu! 🎉'); }"
            53 -> "if(window.Android) { window.Android.shareStory('https://picsum.photos/800/600', 'From Menu', '', ''); }"
            54 -> "if(window.Android) { window.Android.downloadFile('https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 'test.pdf'); }"
            55 -> "if(window.Android) { window.Android.openMediaPreview('https://picsum.photos/800/600', 'photo'); }"
            56 -> "if(window.Android) { window.Android.openLink('https://google.com'); }"
            57 -> "if(window.Android) { window.Android.sendData('{\"from\":\"menu\",\"action\":\"test\"}'); }"
            58 -> "if(window.Android) { window.Android.switchInlineQuery('test query', '[\"users\",\"groups\"]'); }"
            59 -> "if(window.Android) { window.Android.closeApp(); }"

            // === 5. PERMISSIONS ===
            60 -> "if(window.Android) { window.Android.requestWriteAccess(); }"
            61 -> "if(window.Android) { window.Android.requestContact(); }"
            62 -> "if(window.Android) { window.Android.readTextFromClipboard(); }"

            // === 6. STORAGE ===
            70 -> "if(window.Android) { window.Android.cloudStorageSetItem('test_key', 'Hello from Menu ' + Date.now()); window.Android.showToast('Đã lưu test_key'); }"
            71 -> "if(window.Android) { var val = window.Android.cloudStorageGetItem('test_key'); alert('Giá trị: ' + val); }"
            72 -> "if(window.Android) { window.Android.cloudStorageRemoveItem('test_key'); window.Android.showToast('Đã xóa test_key'); }"
            73 -> "if(window.Android) { var keys = window.Android.cloudStorageGetKeys(); alert('Keys: ' + keys); }"

            // === 7. SENSORS ===
            80 -> "if(window.Android) { window.Android.startAccelerometer('ui'); }"
            81 -> "if(window.Android) { window.Android.stopAccelerometer(); }"
            82 -> "if(window.Android) { window.Android.startGyroscope('ui'); }"
            83 -> "if(window.Android) { window.Android.stopGyroscope(); }"
            84 -> "if(window.Android) { window.Android.startDeviceOrientation('ui', false); }"
            85 -> "if(window.Android) { window.Android.stopDeviceOrientation(); }"

            // === 8. LOCATION ===
            90 -> "if(window.Android) { window.Android.getCurrentLocation(); }"
            91 -> "if(window.Android) { window.Android.openLocationSettings(); }"

            // === 9. BIOMETRIC ===
            100 -> "if(window.Android) { var info = window.Android.biometricInit(); alert('Biometric Info: ' + info); }"
            101 -> "if(window.Android) { window.Android.biometricAuthenticate('From Menu'); }"
            102 -> "if(window.Android) { window.Android.biometricOpenSettings(); }"

            // === 10. OTHERS ===
            110 -> "if(window.Android) { window.Android.setEmojiStatus('5368324170671202286', 3600); }"
            111 -> "if(window.Android) { window.Android.addToHomeScreen(); }"
            112 -> "if(window.Android) { window.Android.setBottomBarColor('#4caf50'); }"
            113 -> "if(window.Android) { window.Android.setBottomBarColor('#9c27b0'); }"
            else -> null
        }

        js?.let {
            webView.evaluateJavascript(it, null)
            Toast.makeText(this, "SDK triggered from menu", Toast.LENGTH_SHORT).show()
        }
    }
}


class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun postEvent(eventType: String, eventData: String?) {
        Toast.makeText(context, "Hello", Toast.LENGTH_SHORT).show()
    }
    }
//
//    @JavascriptInterface
//    fun vibrate() {
//        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
//        } else {
//            vibrator.vibrate(100)
//        }
//    }
//
//    @JavascriptInterface
//    fun closeApp() {
//        if (context is Activity) {
//            context.finish()
//        }
//    }
//
//    @JavascriptInterface
//    fun setMainButtonText(text: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnMain = context.findViewById<Button>(R.id.btnMain)
//                btnMain.text = text
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setMainButtonVisible(isVisible: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnMain = context.findViewById<Button>(R.id.btnMain)
//                btnMain.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setMainButtonColor(color: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnMain = context.findViewById<Button>(R.id.btnMain)
//                try {
//                    btnMain.setBackgroundColor(android.graphics.Color.parseColor(color))
//                } catch (e: Exception) {
//                    // Ignore color parse error
//                }
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun openPopup(title: String, message: String, buttonsJson: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val builder = AlertDialog.Builder(context)
//                builder.setTitle(title)
//                builder.setMessage(message)
//                builder.setCancelable(false)
//                try {
//                    val buttons = org.json.JSONArray(buttonsJson)
//
//                    for (i in 0 until buttons.length()) {
//                        val btn = buttons.getJSONObject(i)
//                        val id = btn.optString("id", "")
//                        val type = btn.optString("type", "default")
//                        var text = btn.optString("text", "")
//
//                        if (text.isEmpty()) {
//                            text = when (type) {
//                                "ok" -> "Đồng ý"
//                                "cancel" -> "Hủy"
//                                "destructive" -> "Xóa"
//                                else -> "OK"
//                            }
//                        }
//
//                        val listener = { _: android.content.DialogInterface, _: Int -> sendPopupEvent(id) }
//
//                        when (i) {
//                            0 -> builder.setPositiveButton(text, listener)
//                            1 -> builder.setNegativeButton(text, listener)
//                            2 -> builder.setNeutralButton(text, listener)
//                        }
//                    }
//
//                } catch (e: Exception) {
//                    builder.setPositiveButton("OK (Fallback)") { _, _ -> sendPopupEvent("cancel") }
//                }
//
//                builder.show()
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun hapticFeedback(type: String, style: String) {
//        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            when (type) {
//                "impact" -> {
//                    when (style) {
//                        "light" -> vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
//                        "medium" -> vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
//                        "heavy" -> vibrator.vibrate(VibrationEffect.createOneShot(80, 255))
//                        else -> vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
//                    }
//                }
//                "notification" -> {
//                    when (style) {
//                        "success" -> {
//                            val timing = longArrayOf(0, 50, 50, 100)
//                            val amplitudes = intArrayOf(0, 100, 0, 200)
//                            vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitudes, -1))
//                        }
//                        "warning" -> {
//                            val timing = longArrayOf(0, 50, 100, 200)
//                            val amplitudes = intArrayOf(0, 150, 0, 150)
//                            vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitudes, -1))
//                        }
//                        "error" -> {
//                            val timing = longArrayOf(0, 50, 50, 50, 50, 100)
//                            val amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
//                            vibrator.vibrate(VibrationEffect.createWaveform(timing, amplitudes, -1))
//                        }
//                    }
//                }
//                "selection_change" -> {
//                    vibrator.vibrate(VibrationEffect.createOneShot(10, 50))
//                }
//            }
//        } else {
//            vibrator.vibrate(50)
//        }
//    }
//    @JavascriptInterface
//    fun requestTheme() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                (context as? MainActivity)?.syncTheme()
//            }
//        }
//    }
//    @JavascriptInterface
//    fun openLink(url: String) {
//        try {
//            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
//            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
//            context.startActivity(intent)
//        } catch (e: Exception) {
//            if (context is Activity) {
//                context.runOnUiThread {
//                    Toast.makeText(context, "Cannot open link: $url", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun openTelegramLink(url: String) {
//        openLink(url)
//    }
//    private fun sendPopupEvent(buttonId: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val webView = context.findViewById<WebView>(R.id.webView)
//                val js = "if (window.onAndroidPopupClosed) { window.onAndroidPopupClosed('$buttonId'); }"
//                webView.evaluateJavascript(js, null)
//            }
//        }
//    }
//    @JavascriptInterface
//    fun scanQrCode() {
//        if (context is MainActivity) {
//            context.runOnUiThread {
//                context.checkAndStartQrScan()
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setBackButtonVisible(isVisible: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnBack = context.findViewById<Button>(R.id.btnBack)
//                btnBack.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
//            }
//        }
//    }
//    @JavascriptInterface
//    fun setHeaderColor(colorKeyOrHex: String) {
//        if (context is MainActivity) {
//            context.runOnUiThread {
//                context.updateHeaderColor(colorKeyOrHex)
//            }
//        }
//    }
//    @JavascriptInterface
//    fun setSettingsButtonVisible(isVisible: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnSettings = context.findViewById<Button>(R.id.btnSettings)
//                btnSettings.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
//            }
//        }
//    }
//    @JavascriptInterface
//    fun setMainButtonEnabled(enabled: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnMain = context.findViewById<Button>(R.id.btnMain)
//                btnMain.isEnabled = enabled
//                btnMain.alpha = if (enabled) 1.0f else 0.5f
//            }
//        }
//    }
//    @JavascriptInterface
//    fun setMainButtonProgress(isVisible: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnMain = context.findViewById<Button>(R.id.btnMain)
//                if (isVisible) {
//                    // Save original text and show loading indicator
//                    btnMain.tag = btnMain.text  // Save original text
//                    btnMain.text = "⏳ Đang xử lý..."
//                    btnMain.isEnabled = false
//                    btnMain.alpha = 0.7f
//                } else {
//                    // Restore original text
//                    val originalText = btnMain.tag as? String
//                    if (originalText != null) {
//                        btnMain.text = originalText
//                    }
//                    btnMain.isEnabled = true
//                    btnMain.alpha = 1.0f
//                }
//            }
//        }
//    }
//    @JavascriptInterface
//    fun setClosingConfirmation(needConfirmation: Boolean) {
//        if (context is MainActivity) {
//            context.runOnUiThread {
//                (context as MainActivity).setNeedCloseConfirmation(needConfirmation)
//            }
//        }
//    }
//    @JavascriptInterface
//    fun expandViewport() {
//        if (context is MainActivity) {
//            context.runOnUiThread {
//                // Đánh dấu là đã expanded
//                context.setExpanded(true)
//
//                // Ẩn AppBarLayout
//                val toolbar = context.findViewById<Toolbar>(R.id.toolbar)
//                val parentAppBar = toolbar?.parent as? android.view.View
//                parentAppBar?.visibility = android.view.View.GONE
//                val contentLayout = context.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.contentLayout)
//                val params = contentLayout?.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
//                params?.behavior = null
//                contentLayout?.requestLayout()
//                // Làm cho nội dung vẽ full screen
//                val window = context.window
//                window.statusBarColor = android.graphics.Color.TRANSPARENT
//
//                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
//
//                // Xóa padding của main container
//                val mainContainer = context.findViewById<android.view.View>(R.id.main)
//                mainContainer?.setPadding(0, 0, 0, mainContainer.paddingBottom)
//
//                // Chờ layout recalculate
//                val webView = context.findViewById<WebView>(R.id.webView)
//                webView.postDelayed({
//                    val js = """
//                    if (window.onViewportExpanded) {
//                        window.onViewportExpanded(${webView.height}, ${webView.width});
//                    }
//                """
//                    webView.evaluateJavascript(js, null)
//                }, 200)
//            }
//        }
//    }
//    // Cloud Storage using SharedPreferences
//    @JavascriptInterface
//    fun cloudStorageSetItem(key: String, value: String) {
//        val prefs = context.getSharedPreferences("TelegramCloudStorage", Context.MODE_PRIVATE)
//        prefs.edit().putString(key, value).apply()
//    }
//
//    @JavascriptInterface
//    fun cloudStorageGetItem(key: String): String {
//        val prefs = context.getSharedPreferences("TelegramCloudStorage", Context.MODE_PRIVATE)
//        return prefs.getString(key, "") ?: ""
//    }
//
//    @JavascriptInterface
//    fun cloudStorageRemoveItem(key: String) {
//        val prefs = context.getSharedPreferences("TelegramCloudStorage", Context.MODE_PRIVATE)
//        prefs.edit().remove(key).apply()
//    }
//
//    @JavascriptInterface
//    fun cloudStorageGetKeys(): String {
//        val prefs = context.getSharedPreferences("TelegramCloudStorage", Context.MODE_PRIVATE)
//        val keys = prefs.all.keys.toList()
//        return org.json.JSONArray(keys).toString()
//    }
//    @JavascriptInterface
//    fun biometricInit(): String {
//        val biometricManager = BiometricManager.from(context)
//        val canAuthenticate = biometricManager.canAuthenticate(
//            BiometricManager.Authenticators.BIOMETRIC_STRONG or
//                    BiometricManager.Authenticators.BIOMETRIC_WEAK
//        )
//
//        val available = canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
//        val type = when {
//            canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS -> "finger"
//            else -> ""
//        }
//
//        return """{"available":$available,"type":"$type","access_requested":true,"access_granted":$available}"""
//    }
//    @JavascriptInterface
//    fun biometricAuthenticate(reason: String) {
//        if (context is FragmentActivity) {
//            context.runOnUiThread {
//                val executor = ContextCompat.getMainExecutor(context)
//                val webView = context.findViewById<WebView>(R.id.webView)
//
//                val callback = object : BiometricPrompt.AuthenticationCallback() {
//                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
//                        val token = "biometric_token_" + System.currentTimeMillis()
//                        webView.evaluateJavascript(
//                            "if(window.onBiometricResult) { window.onBiometricResult(true, '$token'); }",
//                            null
//                        )
//                    }
//
//                    override fun onAuthenticationFailed() {
//                        webView.evaluateJavascript(
//                            "if(window.onBiometricResult) { window.onBiometricResult(false, ''); }",
//                            null
//                        )
//                    }
//
//                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
//                        webView.evaluateJavascript(
//                            "if(window.onBiometricResult) { window.onBiometricResult(false, ''); }",
//                            null
//                        )
//                    }
//                }
//
//                val biometricPrompt = BiometricPrompt(context, executor, callback)
//
//                val promptInfo = BiometricPrompt.PromptInfo.Builder()
//                    .setTitle("Xác thực sinh trắc học")
//                    .setSubtitle(reason)
//                    .setNegativeButtonText("Hủy")
//                    .build()
//
//                biometricPrompt.authenticate(promptInfo)
//            }
//        }
//    }
//    @JavascriptInterface
//    fun biometricOpenSettings() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                try {
//                    val intent = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
//                    context.startActivity(intent)
//                } catch (e: Exception) {
//                    Toast.makeText(context, "Không thể mở Settings", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//    // --- Secondary Button ---
//    @JavascriptInterface
//    fun setSecondaryButtonText(text: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                // Lưu ý: Bạn cần thêm Button với id btnSecondary vào activity_main.xml hoặc layout tương ứng
//                val btnSecondary = context.findViewById<Button>(R.id.btnSecondary)
//                btnSecondary?.text = text
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setSecondaryButtonVisible(isVisible: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnSecondary = context.findViewById<Button>(R.id.btnSecondary)
//                btnSecondary?.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setSecondaryButtonColor(color: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnSecondary = context.findViewById<Button>(R.id.btnSecondary)
//                try {
//                    btnSecondary?.setBackgroundColor(android.graphics.Color.parseColor(color))
//                } catch (e: Exception) { }
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setSecondaryButtonEnabled(enabled: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnSecondary = context.findViewById<Button>(R.id.btnSecondary)
//                btnSecondary?.isEnabled = enabled
//                btnSecondary?.alpha = if (enabled) 1.0f else 0.5f
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setSecondaryButtonProgress(isVisible: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val btnSecondary = context.findViewById<Button>(R.id.btnSecondary)
//                if (isVisible) {
//                    btnSecondary?.tag = btnSecondary?.text
//                    btnSecondary?.text = "⏳..."
//                    btnSecondary?.isEnabled = false
//                } else {
//                    val originalText = btnSecondary?.tag as? String
//                    if (originalText != null) btnSecondary?.text = originalText
//                    btnSecondary?.isEnabled = true
//                }
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun setSecondaryButtonPosition(position: String) {
//        // Có thể implement logic thay đổi vị trí button nếu cần
//    }
//
//    // --- Swipe Behavior ---
//    @JavascriptInterface
//    fun setSwipeEnabled(enabled: Boolean) {
//        if (context is MainActivity) {
//            context.setSwipeEnabled(enabled)
//        }
//    }
//
//    // --- Invoice ---
//    @JavascriptInterface
//    fun openInvoice(slug: String) {
//        if (context is MainActivity) {
//            context.openInvoice(slug)
//        }
//    }
//
//    // --- Fullscreen ---
//    @JavascriptInterface
//    fun setFullscreen(fullscreen: Boolean) {
//        if (context is MainActivity) {
//            context.setFullscreen(fullscreen)
//        }
//    }
//
//    // --- Share ---
//    @JavascriptInterface
//    fun shareText(text: String) {
//        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
//        intent.type = "text/plain"
//        intent.putExtra(android.content.Intent.EXTRA_TEXT, text)
//        context.startActivity(android.content.Intent.createChooser(intent, "Share"))
//    }
//    // --- Request Write Access ---
//    @JavascriptInterface
//    fun requestWriteAccess() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                AlertDialog.Builder(context)
//                    .setTitle("Yêu cầu quyền")
//                    .setMessage("Bot muốn gửi tin nhắn cho bạn. Bạn có đồng ý không?")
//                    .setPositiveButton("Đồng ý") { _, _ ->
//                        // Gửi event về Mini App
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('write_access_requested', {detail: {status: 'allowed'}}));",
//                            null
//                        )
//                    }
//                    .setNegativeButton("Từ chối") { _, _ ->
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('write_access_requested', {detail: {status: 'declined'}}));",
//                            null
//                        )
//                    }
//                    .show()
//            }
//        }
//    }
//
//    // --- Request Contact ---
//    @JavascriptInterface
//    fun requestContact() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                AlertDialog.Builder(context)
//                    .setTitle("Chia sẻ số điện thoại")
//                    .setMessage("Mini App muốn biết số điện thoại của bạn. Bạn có đồng ý chia sẻ không?")
//                    .setPositiveButton("Đồng ý") { _, _ ->
//                        // Gửi event với mock contact data
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        val contactJson = """{
//                        "phone_number": "+84123456789",
//                        "first_name": "User",
//                        "last_name": "Test",
//                        "user_id": 999999
//                    }""".trimIndent().replace("\n", "")
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('phone_requested', {detail: {status: 'sent', contact: $contactJson}}));",
//                            null
//                        )
//                    }
//                    .setNegativeButton("Từ chối") { _, _ ->
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('phone_requested', {detail: {status: 'cancelled'}}));",
//                            null
//                        )
//                    }
//                    .show()
//            }
//        }
//    }
//    // --- Bottom Bar Color ---
//    @JavascriptInterface
//    fun setBottomBarColor(color: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                try {
//                    val parsedColor = android.graphics.Color.parseColor(color)
//                    // Set Navigation Bar Color
//                    context.window.navigationBarColor = parsedColor
//                    Toast.makeText(context, "Bottom Bar Color: $color", Toast.LENGTH_SHORT).show()
//                } catch (e: Exception) {
//                    Toast.makeText(context, "Invalid color: $color", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    // --- Emoji Status ---
//    @JavascriptInterface
//    fun setEmojiStatus(emojiId: String, duration: Int) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                // In real Telegram, this would set the user's emoji status
//                // For our mock, we just show a dialog and fake success
//                AlertDialog.Builder(context)
//                    .setTitle("Set Emoji Status")
//                    .setMessage("Emoji ID: $emojiId\nDuration: ${duration}s\n\n(Tính năng này chỉ hoạt động trên Telegram thật)")
//                    .setPositiveButton("OK") { _, _ ->
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('emoji_status_set', {detail: {success: true}}));",
//                            null
//                        )
//                    }
//                    .show()
//            }
//        }
//    }
//    // --- Add to Home Screen ---
//    @JavascriptInterface
//    fun addToHomeScreen() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                // Normally would use ShortcutManager for Android 7.1+
//                // For demo, just show success
//                AlertDialog.Builder(context)
//                    .setTitle("Thêm vào Home Screen")
//                    .setMessage("Tạo shortcut cho Mini App trên màn hình chính?")
//                    .setPositiveButton("Thêm") { _, _ ->
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('home_screen_added', {detail: {status: 'added'}}));",
//                            null
//                        )
//                        Toast.makeText(context, "Đã thêm vào Home Screen!", Toast.LENGTH_SHORT).show()
//                    }
//                    .setNegativeButton("Hủy", null)
//                    .show()
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun checkHomeScreenStatus(): String {
//        return """{"status":"unknown"}"""
//    }
//
//    // --- Accelerometer ---
//    private var accelerometerListener: android.hardware.SensorEventListener? = null
//
//    @JavascriptInterface
//    fun startAccelerometer(refreshRate: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
//                val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
//                val webView = context.findViewById<WebView>(R.id.webView)
//
//                accelerometerListener = object : android.hardware.SensorEventListener {
//                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
//                        val x = event.values[0]
//                        val y = event.values[1]
//                        val z = event.values[2]
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('accelerometer_changed', {detail: {x: $x, y: $y, z: $z}}));",
//                            null
//                        )
//                    }
//                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
//                }
//                sensorManager.registerListener(accelerometerListener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun stopAccelerometer() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
//                accelerometerListener?.let { sensorManager.unregisterListener(it) }
//            }
//        }
//    }
//
//    // --- Gyroscope ---
//    private var gyroscopeListener: android.hardware.SensorEventListener? = null
//
//    @JavascriptInterface
//    fun startGyroscope(refreshRate: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
//                val gyroscope = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)
//                val webView = context.findViewById<WebView>(R.id.webView)
//
//                gyroscopeListener = object : android.hardware.SensorEventListener {
//                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
//                        val x = event.values[0]
//                        val y = event.values[1]
//                        val z = event.values[2]
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('gyroscope_changed', {detail: {x: $x, y: $y, z: $z}}));",
//                            null
//                        )
//                    }
//                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
//                }
//                sensorManager.registerListener(gyroscopeListener, gyroscope, android.hardware.SensorManager.SENSOR_DELAY_UI)
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun stopGyroscope() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
//                gyroscopeListener?.let { sensorManager.unregisterListener(it) }
//            }
//        }
//    }
//    // --- Device Orientation ---
//    private var orientationListener: android.hardware.SensorEventListener? = null
//
//    @JavascriptInterface
//    fun startDeviceOrientation(refreshRate: String, needAbsolute: Boolean) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
//                val orientationSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
//                val webView = context.findViewById<WebView>(R.id.webView)
//
//                orientationListener = object : android.hardware.SensorEventListener {
//                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
//                        val rotationMatrix = FloatArray(9)
//                        android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
//                        val orientation = FloatArray(3)
//                        android.hardware.SensorManager.getOrientation(rotationMatrix, orientation)
//
//                        val alpha = Math.toDegrees(orientation[0].toDouble())
//                        val beta = Math.toDegrees(orientation[1].toDouble())
//                        val gamma = Math.toDegrees(orientation[2].toDouble())
//
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('device_orientation_changed', {detail: {alpha: $alpha, beta: $beta, gamma: $gamma, absolute: $needAbsolute}}));",
//                            null
//                        )
//                    }
//                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
//                }
//                sensorManager.registerListener(orientationListener, orientationSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
//            }
//        }
//    }
//
//    @JavascriptInterface
//    fun stopDeviceOrientation() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
//                orientationListener?.let { sensorManager.unregisterListener(it) }
//            }
//        }
//    }
//
//    // --- Location Manager ---
//    @JavascriptInterface
//    fun openLocationSettings() {
//        if (context is Activity) {
//            val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
//            context.startActivity(intent)
//        }
//    }
//
//    @JavascriptInterface
//    fun getCurrentLocation() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val webView = context.findViewById<WebView>(R.id.webView)
//
//                // Check permission first
//                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
//                    != PackageManager.PERMISSION_GRANTED) {
//                    // Request permission (you may need to handle this in MainActivity)
//                    webView?.evaluateJavascript(
//                        "window.dispatchEvent(new CustomEvent('location_error', {detail: {error: 'Location permission denied'}}));",
//                        null
//                    )
//                    return@runOnUiThread
//                }
//
//                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
//                val location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
//                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
//
//                if (location != null) {
//                    webView?.evaluateJavascript(
//                        "window.dispatchEvent(new CustomEvent('location_received', {detail: {latitude: ${location.latitude}, longitude: ${location.longitude}, accuracy: ${location.accuracy}}}));",
//                        null
//                    )
//                } else {
//                    webView?.evaluateJavascript(
//                        "window.dispatchEvent(new CustomEvent('location_error', {detail: {error: 'Could not get location'}}));",
//                        null
//                    )
//                }
//            }
//        }
//    }
//    // --- Story Widget ---
//    @JavascriptInterface
//    fun shareStory(mediaUrl: String, text: String, widgetLinkUrl: String, widgetLinkName: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                // In a real Telegram client, this would open the Stories composer
//                // For our demo, we'll show a dialog with the content
//                val message = """
//                Media URL: $mediaUrl
//                Caption: $text
//                Widget Link: $widgetLinkUrl
//                Widget Name: $widgetLinkName
//
//                (Trong Telegram thật, nội dung này sẽ được mở trong Story Editor)
//            """.trimIndent()
//
//                AlertDialog.Builder(context)
//                    .setTitle("📖 Share to Story")
//                    .setMessage(message)
//                    .setPositiveButton("Chia sẻ") { _, _ ->
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('story_shared', {detail: {success: true}}));",
//                            null
//                        )
//                        Toast.makeText(context, "Đã chia sẻ lên Story!", Toast.LENGTH_SHORT).show()
//                    }
//                    .setNegativeButton("Hủy") { _, _ ->
//                        val webView = context.findViewById<WebView>(R.id.webView)
//                        webView?.evaluateJavascript(
//                            "window.dispatchEvent(new CustomEvent('story_shared', {detail: {success: false}}));",
//                            null
//                        )
//                    }
//                    .show()
//            }
//        }
//    }
//    // --- Download File ---
//    @JavascriptInterface
//    fun downloadFile(url: String, fileName: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                try {
//                    val webView = context.findViewById<WebView>(R.id.webView)
//
//                    // Notify Mini App that download started
//                    webView?.evaluateJavascript(
//                        "window.dispatchEvent(new CustomEvent('file_download_started', {detail: {url: '$url', file_name: '$fileName'}}));",
//                        null
//                    )
//
//                    // Use DownloadManager
//                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
//                    request.setTitle(fileName)
//                    request.setDescription("Downloading from Mini App...")
//                    request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//                    request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
//
//                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
//                    downloadManager.enqueue(request)
//
//                    Toast.makeText(context, "Đang tải: $fileName", Toast.LENGTH_SHORT).show()
//
//                } catch (e: Exception) {
//                    val webView = context.findViewById<WebView>(R.id.webView)
//                    webView?.evaluateJavascript(
//                        "window.dispatchEvent(new CustomEvent('file_download_error', {detail: {error: '${e.message}'}}));",
//                        null
//                    )
//                    Toast.makeText(context, "Lỗi tải file: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    // --- Open Media Preview ---
//    @JavascriptInterface
//    fun openMediaPreview(url: String, type: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                try {
//                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
//                    intent.setDataAndType(
//                        android.net.Uri.parse(url),
//                        if (type == "video") "video/*" else "image/*"
//                    )
//                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
//                    context.startActivity(intent)
//                } catch (e: Exception) {
//                    // Fallback: Open in browser
//                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
//                    context.startActivity(intent)
//                }
//            }
//        }
//    }
//    // --- Read Text From Clipboard ---
//    @JavascriptInterface
//    fun readTextFromClipboard() {
//        if (context is Activity) {
//            context.runOnUiThread {
//                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
//                val webView = context.findViewById<WebView>(R.id.webView)
//
//                val clipData = clipboard.primaryClip
//                if (clipData != null && clipData.itemCount > 0) {
//                    val text = clipData.getItemAt(0).text?.toString() ?: ""
//                    webView?.evaluateJavascript(
//                        "window.dispatchEvent(new CustomEvent('clipboard_text_received', {detail: {data: '$text'}}));",
//                        null
//                    )
//                } else {
//                    webView?.evaluateJavascript(
//                        "window.dispatchEvent(new CustomEvent('clipboard_text_received', {detail: {data: null, error: 'Clipboard is empty'}}));",
//                        null
//                    )
//                }
//            }
//        }
//    }
//
//    // --- Send Data (to Bot) ---
//    @JavascriptInterface
//    fun sendData(data: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                // In real Telegram, this sends data to bot and closes the Mini App
//                // For our demo, we show a dialog
//                AlertDialog.Builder(context)
//                    .setTitle("📤 Send Data to Bot")
//                    .setMessage("Data: $data\n\n(Trong Telegram thật, Mini App sẽ đóng và data được gửi tới bot)")
//                    .setPositiveButton("Gửi & Đóng") { _, _ ->
//                        Toast.makeText(context, "Đã gửi data tới Bot!", Toast.LENGTH_SHORT).show()
//                        // Close the Mini App (simulating Telegram behavior)
//                        context.finish()
//                    }
//                    .setNegativeButton("Hủy", null)
//                    .show()
//            }
//        }
//    }
//    // --- Switch Inline Query ---
//    @JavascriptInterface
//    fun switchInlineQuery(query: String, chatTypesJson: String) {
//        if (context is Activity) {
//            context.runOnUiThread {
//                // In real Telegram, this switches to another chat and inserts @bot query
//                // For our demo, we show a dialog
//                AlertDialog.Builder(context)
//                    .setTitle("🔍 Switch Inline Query")
//                    .setMessage("Query: @bot $query\nChat Types: $chatTypesJson\n\n(Trong Telegram thật, sẽ mở chat picker và chèn inline query)")
//                    .setPositiveButton("OK") { _, _ ->
//                        Toast.makeText(context, "Inline query: $query", Toast.LENGTH_SHORT).show()
//                    }
//                    .show()
//            }
//        }
//    }
