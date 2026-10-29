# Add project specific ProGuard rules here.
# 注意：本项目使用 Storage Access Framework 的 Uri 访问文件，ProGuard 不需要额外保留规则。
-keepattributes Signature
-keep class com.wifivault.app.server.** { *; }
