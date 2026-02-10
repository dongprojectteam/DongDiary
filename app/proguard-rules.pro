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

# Google API Client 관련 클래스 유지
-keep class com.google.api.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.json.gson.** { *; }

# JSON 직렬화 관련 (Google API Client 내부용)
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# 구글 인증 관련
-keep class com.google.auth.** { *; }
-dontwarn com.google.auth.**
-dontwarn com.google.api.client.**

# Apache HTTP 관련 미싱 클래스 경고 무시
-dontwarn org.apache.http.**
-dontwarn android.net.http.**

# javax.naming (안드로이드에 없음) 관련 경고 무시
-dontwarn javax.naming.**

# Google API Client가 사용하는 기타 누락 클래스 처리
-dontwarn com.google.j2objc.annotations.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# 필수적인 구글 라이브러리 유지 (앞서 드린 규칙에 더해 확실히 추가)
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.json.gson.GsonFactory { *; }