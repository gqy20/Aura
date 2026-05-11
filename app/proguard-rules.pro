# Add project specific ProGuard rules here.
-dontwarn kotlinx.serialization.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class **$$serializer { *; }
-keepclassmembers class ** {
    public static ** Companion;
}

-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
