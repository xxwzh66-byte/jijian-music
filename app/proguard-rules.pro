# V1.1.8 第一版只做 shrink + optimize，不改名。
#
# 体积的大头是「一整片从未被调用的库代码」，删掉就够了；而改名要同时说服
# Gson（JSON 字段名）、Room（生成的 *_Impl）、media3（SessionToken 的
# ComponentName）、Compose（$default 合成方法）四套反射约定，收益远小于风险。
# 等这几处都有实机回归之后，再单独一个版本开 naming。
-dontobfuscate

# ---- Gson：反射序列化的模型必须保留字段名 ----
#
# V1.1.8 起 release 开启 R8 混淆。备份文件（backup.json）、收藏/历史/歌单里
# 存的是 MusicInfo 的 JSON 快照，字段名就是协议本身：混淆之后旧备份读不回来，
# 用户数据直接报废。所以这几个类整体 keep，连字段名一起。
-keep class com.lxmusic.android.data.model.** { *; }
-keep class com.lxmusic.android.data.backup.BackupFile { *; }
-keep class com.lxmusic.android.data.backup.BackupPlaylist { *; }
-keep class com.lxmusic.android.data.backup.BackupHistoryRow { *; }
-keep class com.lxmusic.android.data.backup.BackupEventRow { *; }
-keep class com.lxmusic.android.data.settings.EqProfile { *; }

# Gson 自身的泛型签名与注解
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod
-dontwarn sun.misc.**

# ---- Room ----
#
# Room 在编译期生成 *_Impl，运行时靠反射找 RoomDatabase 的子类；
# 实体字段由生成的代码直接读写，但 schema 校验与 Migration 走列名常量，
# 类名被改名不影响。保留数据库与实体的签名即可。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---- media3 / ExoPlayer ----
#
# PlaybackService 由系统绑定，SessionToken 传的是 ComponentName，
# 混淆类名会让 MediaSession 的连接断开。
-keep class com.lxmusic.android.player.PlaybackService { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# ---- 应用入口 ----
-keep class com.lxmusic.android.LxApp { *; }
-keep class com.lxmusic.android.MainActivity { *; }
-keep class com.lxmusic.android.update.UpdateInstaller { *; }

# ---- Compose ----
# Compose 运行时不需要额外规则；这里只关掉 R8 对它的噪音警告。
-dontwarn androidx.compose.**

# ---- OkHttp / Kotlin ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class kotlin.Metadata { public <methods>; }
