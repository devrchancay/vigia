# Ktor's IntellijIdeaDebugDetector references java.lang.management, which does not exist on Android. The code
# path only runs under an IDE debugger on the JVM, so the missing classes are never loaded on a device.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
