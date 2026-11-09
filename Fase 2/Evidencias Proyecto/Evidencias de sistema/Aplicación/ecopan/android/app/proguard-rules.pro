# R8 rules for the Android app only. The desktop app keeps its own in jvmApp/proguard-rules.pro.
#
# Libraries ship their own keep rules inside their AARs and jars (kotlinx.serialization,
# coroutines, Room, DataStore, WorkManager, Glance, Firebase, Koin ...), and AGP adds keeps for
# everything the manifest names. What stays here is only what the app itself needs.

# Compose emits trace events for composition tracing, which no release build records, so R8 may
# drop the calls together with the strings they carry. (The sourceInformation calls are dropped by
# the rules the Compose runtime ships; its trace calls are not.)
-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
    void traceEventStart(int, java.lang.String);
    void traceEventEnd();
}
