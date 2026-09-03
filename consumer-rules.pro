# Actos Kotlin SDK — Consumer ProGuard & R8 Rules
# Automatically included in consuming Android / JVM applications when using the SDK.

# Preserve Kotlin Serialization metadata & annotations
-keepattributes *Annotation*, InnerClasses, Signature

# Preserve @Serializable models and their fields
-keep @kotlinx.serialization.Serializable class dev.actos.model.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class dev.actos.model.** {
    *** Companion;
}

# Preserve serializer classes and companion serializer() methods
-keep class * implements kotlinx.serialization.KSerializer {
    <init>(...);
    public <methods>;
}
-keepclasseswithmembers class dev.actos.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve Actos core and blocking API entry points
-keep class dev.actos.Actos { *; }
-keep class dev.actos.blocking.BlockingActos { *; }
-keep class dev.actos.Page { *; }
-keep class dev.actos.Patch { *; }
-keep class dev.actos.RateLimit { *; }
-keep class dev.actos.ActosException { *; }
-keep class dev.actos.ActosApiException { *; }
-keep class dev.actos.NotFoundException { *; }
-keep class dev.actos.GoneException { *; }
-keep class dev.actos.RateLimitException { *; }
-keep class dev.actos.ValidationException { *; }
-keep class dev.actos.ForbiddenException { *; }
-keep class dev.actos.BannedException { *; }
-keep class dev.actos.ConflictException { *; }
-keep class dev.actos.AuthenticationException { *; }
-keep class dev.actos.InvalidKeyException { *; }
-keep class dev.actos.InvalidCursorException { *; }
-keep class dev.actos.UnsupportedMediaException { *; }
-keep class dev.actos.InternalServerException { *; }
-keep class dev.actos.NetworkException { *; }
