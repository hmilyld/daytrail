# 高德地图 / 定位 SDK
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**

# release 包剥离调试日志
#
# 不依赖 AGP 默认配置：不同 AGP 版本附带的 proguard-android*.txt 是否包含
# Log 的 assumenosideeffects 并不一致，显式声明才可靠。保留 w/e，
# 让真正的异常仍在 logcat 可见（不含坐标，见 DiagnosticLog 的设计）。
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# 隐藏源文件名，减少包内的路径指纹
-renamesourcefileattribute SourceFile

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hmilyld.daytrail.**$$serializer { *; }
-keepclassmembers class com.hmilyld.daytrail.** {
    *** Companion;
}
-keepclasseswithmembers class com.hmilyld.daytrail.** {
    kotlinx.serialization.KSerializer serializer(...);
}
