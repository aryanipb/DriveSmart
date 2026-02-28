# Keep model metadata and reflection-friendly classes if needed by LiteRT runtime.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
