# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# 保留注解、签名和内部类，这对 DataBinding 和 Coroutines 很重要
-keepattributes *Annotation*, Signature, InnerClasses

# XLog 混淆规则
-keep class com.elvishew.xlog.** { *; }
-dontwarn com.elvishew.xlog.**

# DataBinding 相关
-keep class android.databinding.** { *; }
-keep class androidx.databinding.** { *; }
-keep class * extends androidx.databinding.ViewDataBinding { *; }
-keep public class * extends androidx.databinding.library.baseAdapters.BindingAdapter

# 保留项目中的实体类或通过反射访问的类
# 如果你有 Serializable 实体类，保留它们
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 移除之前过于宽泛的规则:
# -keep class com.liziwa.hisense_autorefresh.** { *; }
# -keepclassmembers class com.liziwa.hisense_autorefresh.** { *; }
