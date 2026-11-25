# Keep line numbers for readable crash traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# zstd-jni loads native methods by reflection/JNI.
-keep class com.github.luben.zstd.** { *; }
