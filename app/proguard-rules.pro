# Project-specific R8/ProGuard rules.
#
# Release builds currently ship unminified (isMinifyEnabled = false in
# app/build.gradle.kts), so this file is inert until that flag flips — CI never
# exercises it. Enable minify and run a release build locally/CI before trusting it.
#
# The kotlinx-serialization runtime (1.11.0) and androidx.credentials both ship their
# own consumer rules inside their artifacts, so nothing third-party is kept wholesale
# here; what follows only pins the app's own reflective edges.

# ── kotlinx.serialization ─────────────────────────────────────────────────────
# @Serializable models — PasskeyEntity, the update manifest, and the nav3 NavKeys —
# compile to a generated $$serializer plus a named Companion. Direct call sites are
# wired by the compiler plugin, but serializer lookups that go through a KClass
# (nav saved-state restoration, typeOf-based decode) resolve the Companion reflectively
# and depend on serial names surviving as-is.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class com.stitch.authenticator.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.stitch.authenticator.** {
    public static ** Companion;
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.stitch.authenticator.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepnames @kotlinx.serialization.Serializable class com.stitch.authenticator.**

# ── Credential provider ───────────────────────────────────────────────────────
# PasskeyProviderService is bound by the name declared in AndroidManifest.xml and
# PasskeyCredentialActivity is launched through PendingIntents the system hands out —
# AGP's default rules keep every manifest-registered component, so no -keep is needed
# for either. Deliberately no blanket keep for androidx.credentials or biometricprompt:
# the libraries resolve nothing through user-code reflection, and pinning them would
# only protect code R8 can already trace.

# ── Android Keystore / crypto ─────────────────────────────────────────────────
# java.security/javax.crypto are platform classes that R8 never strips; the key ALIASES
# are runtime data (Keystore-backed keys are looked up by name at use time), so no
# rules apply here either. Recorded so the next reader does not go looking for them.
