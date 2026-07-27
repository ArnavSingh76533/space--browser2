# SPACE release rules. OkHttp/Room/Compose ship their own consumer rules;
# only project-specific keeps live here.

# Keep JS-facing names if a JavascriptInterface is ever added.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
