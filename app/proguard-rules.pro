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

-dontwarn com.alibaba.fastjson.**
-keep class com.alibaba.fastjson.** {*;}
-dontwarn org.greenrobot.greendao.**
-keep class org.greenrobot.greendao.** {*;}

-dontwarn com.hfims.android.core.**
-keep class com.hfims.android.core.** {*;}
-dontwarn com.hfims.android.lib.palm.**
-keep class com.hfims.android.lib.palm.** {*;}
-dontwarn com.xinran.**
-keep class com.xinran.** {*;}
-dontwarn com.veinauthen.**
-keep class com.veinauthen.** {*;}

-keep class com.hjq.permissions.** {*;}
-dontwarn com.hjq.permissions.**