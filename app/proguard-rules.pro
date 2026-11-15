# 高德地图 / 定位 SDK
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**

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
