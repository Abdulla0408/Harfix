# The web page calls these through window.HarfixAndroid.
-keepclassmembers class uz.harfix.app.Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
