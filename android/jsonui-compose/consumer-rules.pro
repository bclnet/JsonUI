# QuickJS uses JNI; keep the bridge interface so its methods stay callable from native code.
-keep interface com.bclnet.jsonui.compose.QuickJsHostBridge { *; }
-keep class * implements com.bclnet.jsonui.compose.QuickJsHostBridge { *; }
