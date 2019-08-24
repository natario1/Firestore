# Seems that keepnames is not enough.
-keep class * implements com.otaliastudios.firestore.FirestoreMetadata { *; }
-keep class * extends com.otaliastudios.firestore.FirestoreMap { *; }
-keep class * extends com.otaliastudios.firestore.FirestoreList { *; }
-keep class * extends com.otaliastudios.firestore.FirestoreDocument { *; }
