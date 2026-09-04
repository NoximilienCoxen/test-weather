# Regole per R8, che sulla release adesso gira davvero.
#
# Il flag e le regole vanno insieme: accendere la minificazione senza dire cosa
# tenere, con kotlinx.serialization in gioco, rompe la deserializzazione **in
# silenzio** - l'app compila, si installa, e poi non legge piu' una previsione.
# E' il tipo di guasto che si scopre in mano, non in CI.

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# La libreria porta gia' le proprie regole dentro l'artefatto, e in condizioni
# normali basterebbero. Sono ripetute qui apposta: i DTO di data/ sono l'unico
# punto in cui l'app dipende dalla riflessione sui nomi, e una regola che si da'
# per scontata e' una regola che nessuno controlla quando cambia il minificatore.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses

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

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Quello che il manifest non nomina
# ---------------------------------------------------------------------------
# Activity e receiver dichiarati nel manifest li tiene AGP da se'. Il parser del
# feed invece arriva a XmlPullParser per riflessione dentro la piattaforma, e le
# eccezioni di org.xmlpull non vanno rinominate.
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }
