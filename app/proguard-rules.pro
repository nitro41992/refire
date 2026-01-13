# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep NotificationListenerService
-keep class com.narasimha.refire.service.ReFireNotificationListener { *; }

# Keep data models for Room (Phase 2)
-keep class com.narasimha.refire.data.model.** { *; }
