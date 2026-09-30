# kotlinx.serialization keeps generated serializers for @Serializable models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.piyokey.** {
    *** Companion;
}
-keepclasseswithmembers class app.piyokey.** {
    kotlinx.serialization.KSerializer serializer(...);
}
