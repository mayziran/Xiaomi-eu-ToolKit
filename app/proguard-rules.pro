# libxposed API / service are provided by the framework at runtime.
-dontwarn io.github.libxposed.api.**
-dontwarn io.github.libxposed.service.**

# The module entry point is referenced by name from META-INF/xposed/java_init.list.
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class com.xiaomieu.toolkit.xposed.HookEntry { *; }

# Keep libxposed modules and hookers.
-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public void onPackageLoaded(...);
    public void onPackageReady(...);
    public void onSystemServerStarting(...);
}
-keep,allowshrinking,allowoptimization,allowobfuscation class ** implements io.github.libxposed.api.XposedInterface$Hooker
-keepclassmembers,allowoptimization class ** implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain);
}
