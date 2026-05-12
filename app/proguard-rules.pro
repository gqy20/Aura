# Add project specific ProGuard rules here.
-dontwarn kotlinx.serialization.**

# Timber: AppLogger controls Debug/Release filtering through SafeLogTree.
# Keep this narrow so AppLogger.warn/error remain available in release builds.
-assumenosideeffects class timber.log.Timber {
    static *** d(...);
    static *** v(...);
}

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class **$$serializer { *; }
-keepclassmembers class ** {
    public static ** Companion;
}

-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
