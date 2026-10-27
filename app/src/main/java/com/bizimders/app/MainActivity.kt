package com.bizimders.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

private const val HOST = "appassets.androidplatform.net"
private const val START_URL = "https://$HOST/assets/index.html"

/** Dosya kaydetme penceresi: (dosya adı, MIME türü) -> seçilen Uri */
class CreateDoc : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBytes: ByteArray? = null

    // <input type="file"> (Excel içe aktarma, yedek yükleme) için dosya seçici
    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    // Excel / Word / yedek dosyalarını kaydetme (izin gerektirmez)
    private val createDocument =
        registerForActivityResult(CreateDoc()) { uri: Uri? ->
            val bytes = pendingBytes
            pendingBytes = null
            if (uri == null || bytes == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                toast("Dosya kaydedildi")
            } catch (e: Exception) {
                toast("Kaydedilemedi: ${e.message}")
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this)
        val root = FrameLayout(this).apply {
            fitsSystemWindows = true
            addView(
                webView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        setContentView(root)

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // localStorage: şifreler ve programlar burada saklanır
            allowFileAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100
            mediaPlaybackRequiresUserGesture = true
        }
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) { }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                // window.print() çağrılarını Android yazdırma hizmetine (PDF'e kaydet dahil) yönlendir
                view.evaluateJavascript("window.print=function(){AndroidBridge.print()};", null)
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                toast("Sayfa yeniden yükleniyor")
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                params: WebChromeClient.FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    toast("Dosya seçici açılamadı")
                    false
                }
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("Tamam") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("Evet") { _, _ -> result.confirm() }
                    .setNegativeButton("Hayır") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult
            ): Boolean {
                val input = EditText(this@MainActivity).apply { setText(defaultValue ?: "") }
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("Tamam") { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton("İptal") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }

        // Geri tuşu: önce açık pencere/menü kapanır, sonra ana ekrana dönülür, en son çıkış sorulur
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val js = """(function(){try{
                    var m=document.getElementById('mdl'); if(m&&!m.hidden){mclose();return 1}
                    var n=document.getElementById('nav'); if(n&&n.classList.contains('open')){drawer(0);return 1}
                    var p=document.getElementById('pop'); if(p&&!p.hidden){p.hidden=true;return 1}
                    var a=document.getElementById('app'); if(a&&!a.hidden&&cur!=='home'){go('home');return 1}
                }catch(e){} return 0})()"""
                webView.evaluateJavascript(js) { r -> if (r != "1") confirmExit() }
            }
        })

        webView.loadUrl(START_URL)
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setMessage("Programdan çıkılsın mı?")
            .setPositiveButton("Çık") { _, _ -> finish() }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /** Web sayfasından çağrılan Android işlevleri (window.AndroidBridge) */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(name: String, base64: String, mime: String) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            runOnUiThread {
                pendingBytes = bytes
                createDocument.launch(Pair(name, if (mime.isBlank()) "application/octet-stream" else mime))
            }
        }

        @JavascriptInterface
        fun print() {
            runOnUiThread {
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = webView.createPrintDocumentAdapter("Ders Programi")
                pm.print(
                    "Ders Programi",
                    adapter,
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                )
            }
        }

        @JavascriptInterface
        fun isApp(): Boolean = true
    }
}
