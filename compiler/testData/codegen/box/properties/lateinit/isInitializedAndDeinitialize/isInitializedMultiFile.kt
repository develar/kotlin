// LANGUAGE: -NativeJsProhibitLateinitIsInitializedIntrinsicWithoutPrivateAccess
// WITH_STDLIB
// On JVM, isInitialized on a property from another file is forbidden because lateinit-ness is not included in the an ABI of a property.
// Native became stable in 1.9.20 while `NativeJsProhibitLateinitIsInitializedIntrinsicWithoutPrivateAccess` was introduced in 1.9. Disabling old features in stable Native is not supported.
// IGNORE_BACKEND_K1: JVM_IR, NATIVE
// In K2, isInitialized on a property from another file is forbidden for all backends.
// IGNORE_BACKEND_K2: ANY
// IGNORE_IR_DESERIALIZATION_TEST: NATIVE
// IGNORE_IR_DESERIALIZATION_TEST: JS_IR
// ^^^ Source code is not compiled in JS.

// DISABLE_IR_VISIBILITY_CHECKS: ANY
// ^ isInitialized results in private field access which is reported by the visibility checker.

// FILE: A.kt

fun test1F(o: Foo) = if (!o::bar.isInitialized)  "1F" else "Fail1F"
fun test1T(o: Foo) = if (o::bar.isInitialized) "1T" else "Fail1T"

// FILE: B.kt

fun test2F(o: Foo) = if (!o::bar.isInitialized) "2F" else "Fail2F"
fun test2T(o: Foo) = if (o::bar.isInitialized) "2T" else "Fail2T"

class Foo {
    lateinit var bar: String

    fun testF() = if (!this::bar.isInitialized) "0F" else "Fail0F"
    fun testT() = if (this::bar.isInitialized) "0T" else "Fail0T"
}


fun test3F(o: Foo) = if (!o::bar.isInitialized) "3F" else "Fail3F"
fun test3T(o: Foo) = if (o::bar.isInitialized) "3T" else "Fail3T"

// FILE: C.kt

fun test4F(o: Foo) = if (!o::bar.isInitialized) "4F" else "Fail4F"
fun test4T(o: Foo) = if (o::bar.isInitialized) "4T" else "Fail4T"


fun box(): String {
    val o = Foo()
    if (o.testF() != "0F") return "Fail0F"
    if (test1F(o) != "1F") return "Fail1F"
    if (test2F(o) != "2F") return "Fail2F"
    if (test3F(o) != "3F") return "Fail3F"
    if (test4F(o) != "4F") return "Fail4F"

    o.bar = "OK"

    if (o.testT() != "0T") return "Fail0T"
    if (test1T(o) != "1T") return "Fail1T"
    if (test2T(o) != "2T") return "Fail2T"
    if (test3T(o) != "3T") return "Fail3T"
    if (test4T(o) != "4T") return "Fail4T"

    return o.bar
}
