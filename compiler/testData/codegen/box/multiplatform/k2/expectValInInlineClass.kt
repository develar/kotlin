// LANGUAGE: +MultiPlatformProjects
// IGNORE_BACKEND_K2: JVM_IR
// IGNORE_HMPP: JVM_IR
// IGNORE_LIGHT_TREE

// MODULE: common
// FILE: common.kt

expect value class Wrapper(val obj: Any) {
    val prop: String // [PROPERTY_WITH_BACKING_FIELD_INSIDE_VALUE_CLASS] Value class cannot have properties with backing fields
}

// MODULE: main()()(common)
// FILE: test.kt

actual value class Wrapper(val obj: Any) {
    actual val prop: String
        get() = "OK"
}

fun box(): String = Wrapper("").prop
