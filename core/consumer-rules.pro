# Consumer proguard rules for core module

# smbj: protocol messages and ASN.1 types are created reflectively. BouncyCastle is referenced directly, so R8 traces it.
-keep class com.hierynomus.** { *; }
-dontwarn org.bouncycastle.**
# mbassador (smbj's event bus) finds @Handler methods by reflection.
-keep class net.engio.mbassy.** { *; }
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.**
-dontwarn org.slf4j.**
