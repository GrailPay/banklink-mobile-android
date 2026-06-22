# Public API of :bank-link — kept under R8 in apps that consume this library.

-keepattributes *Annotation*, InnerClasses

# kotlinx.serialization companions/serializers for @Serializable internal DTOs.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Public surface — explicitly listed so internal/ types remain shrinkable.
-keep public class com.grailpay.banklink.GrailPayBankLink { public *; }
-keep public class com.grailpay.banklink.BankLinkActivity { public *; }
-keep public class com.grailpay.banklink.BankLinkConfig { public *; }
-keep public class com.grailpay.banklink.BankLinkConfig$Builder { public *; }
-keep public class com.grailpay.banklink.BankLinkListener { *; }
-keep public class com.grailpay.banklink.Branding { public *; }
-keep public class com.grailpay.banklink.BrandTheme { *; }
-keep public class com.grailpay.banklink.EntityType { *; }
-keep public class com.grailpay.banklink.events.** { public *; }
-keep public class com.grailpay.banklink.ui.BankLinkContentKt { public *; }
-keep public class com.grailpay.banklink.ui.BrandingThemeKt { public *; }