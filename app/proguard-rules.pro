# openflux.aar, Room, and Compose all ship their own consumer proguard
# rules (bundled in their .aar's proguard.txt) that AGP applies
# automatically - nothing here duplicates those. Add project-specific rules
# below only if a future dependency needs them (an R8 "missing class"
# warning at build time, or a reflection-based crash at runtime, are the
# signals to watch for).

# com.google.crypto.tink (pulled in transitively, likely via androidx
# datastore/security) references these errorprone annotations at compile
# time only - they're not on the runtime classpath and nothing needs them
# there either. Exact rules AGP's own R8 run recommended (see
# app/build/outputs/mapping/release/missing_rules.txt).
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
