# Keep file/line info so release stack traces are readable; hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Hilt, Compose, Navigation (type-safe routes) and kotlinx.serialization ship their own consumer rules,
# so no extra keep rules are needed for them. Add rules here only for libraries that do not.
