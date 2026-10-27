# Release only: the performance logging (PerfLog) is switched off for good, so R8 removes every PerfLog.d { ... }
# call together with the message it builds and the time measurements around it. The perf build does not use this
# file, it keeps the logging.
-assumenosideeffects class com.kllxx.memorylane.core.common.PerfLog {
    boolean isEnabled() return false;
    long nanoTime() return 0;
    long sinceMs(long) return 0;
    void emit*(java.lang.String);
}
