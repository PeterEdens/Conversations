
-dontobfuscate
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-keepattributes EnclosingMethod

-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService
-keep public class org.whispersystems.**

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keep class org.webrtc.**  { *; }

-keep class android.media.AudioTrack.** { *; }

-keep class org.appspot.apprtc.**  { *; }

-keep class com.apple.** { *; }

-keep class de.tavendo.autobahn.**  { *; }


-keepclasseswithmembernames class * { native <methods>; }

-dontwarn javax.jcr.**
-dontwarn org.slf4j.**
-dontwarn javax.servlet.**
-dontwarn org.apache.jackrabbit.**
-dontwarn org.apache.commons.**
-dontwarn org.bouncycastle.**
-dontwarn com.squareup.**
-dontwarn android.app.Notification
-dontwarn sun.misc.Unsafe