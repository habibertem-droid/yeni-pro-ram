# WebView JavaScript arayüzü (AndroidBridge) kod küçültmeden etkilenmesin
-keepclassmembers class com.bizimders.app.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
