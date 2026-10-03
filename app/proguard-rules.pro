# 混淆规则（release 启用 R8）
# Glance 小组件的更新管道内部使用 WorkManager（反射实例化 Worker/InputMerger），
# 缺少 keep 规则时小组件会永远停在加载布局
-keep class androidx.work.** { *; }
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Room 通过反射实例化生成的 *_Impl 类（库自带规则缺失时兜底）
-keep class * extends androidx.room.RoomDatabase { *; }

# ML Kit 文字识别：内部管线经混淆后 NPE（gvo.f/ji4mh4 等混淆名），
# 完整保留 ML Kit 与其依赖的 GMS internal 包
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit**
