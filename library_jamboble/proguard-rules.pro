# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.jianbao.jamboble.* {
public <fields>;
public <methods>;

}
-keep class com.jianbao.jamboble.*.* {
public <fields>;
public <methods>;

}
-keep class com.jianbao.fastble.* {
public <fields>;
public <methods>;

}
-keep class com.jianbao.fastble.JamBoHelper

-keep class com.jianbao.jamboble.fetalheart.FetalHeartHelper

-keep class com.jianbao.fastble.*.* {
public <fields>;
public <methods>;

}
-keep class com.jianbao.jamboble.draw.* {
public <fields>;
public <methods>;

}
-keep class com.jianbao.jamboble.callbacks.* {
public <fields>;
public <methods>;

}
-keep class com.jianbao.jamboble.data.* { *;
}
-keep class com.jianbao.jamboble.device.* {
 public <fields>;
 public <methods>;

 }
 -keep class com.jianbao.jamboble.draw.* {
 public <fields>;
 public <methods>;

 }
-keep class com.jianbao.jamboble.*.*.* {
public <fields>;
public <methods>;

}
-keep class com.creative.*.* {
public <fields>;
public <methods>;

}

-keep class com.qingniu.scale.measure.broadcast.ScaleBroadcastService{*;}
-keep class com.qingniu.scale.measure.ble.ScaleBleService{*;}
-keep class com.qingniu.qnble.scanner.BleScanService{*;}
-keep class com.qingniu.scale.model.BleScaleData{*;}

-keep class * implements android.os.Parcelable{ # 保持Parcelable不被混淆
    public static final android.os.Parcelable.Creator *;
}

#kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
-keepattributes *Annotation*
-keep class org.jetbrains.** { *; }
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
