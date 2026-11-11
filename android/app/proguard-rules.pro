# Gson fills the catalog entries by reflection: keep their fields and the
# generic signatures TypeToken reads. Without these a shrunk build parses
# every catalog as empty and silently shows no models.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class ai.jessejohnston.bookmend.data.RecoveryImage { <fields>; <init>(...); }
-keep class ai.jessejohnston.bookmend.data.GooglebookEntry { <fields>; <init>(...); }
-keep class * extends com.google.gson.reflect.TypeToken
