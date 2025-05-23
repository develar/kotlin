// This file was generated automatically. See  generateTestDataForTypeScriptWithFileExport.kt
// DO NOT MODIFY IT MANUALLY.

// CHECK_TYPESCRIPT_DECLARATIONS
// RUN_PLAIN_BOX_FUNCTION
// SKIP_NODE_JS
// INFER_MAIN_MODULE
// MODULE: JS_TESTS
// FILE: member-properties.kt

@file:JsExport

package foo


external interface SomeExternalInterface


class Test {
    fun sum(x: Int, y: Int): Int =
        x + y

    fun varargInt(vararg x: Int): Int =
        x.size

    fun varargNullableInt(vararg x: Int?): Int =
        x.size

    fun varargWithOtherParameters(x: String, vararg y: String, z: String): Int =
        x.length + y.size + z.length

    fun varargWithComplexType(vararg x: (Array<IntArray>) -> Array<IntArray>): Int =
        x.size

    fun sumNullable(x: Int?, y: Int?): Int =
        (x ?: 0) + (y ?: 0)

    fun defaultParameters(a: String, x: Int = 10, y: String = "OK"): String =
        a + x.toString() + y

    fun <T> generic1(x: T): T = x

    fun <T> generic2(x: T?): Boolean = (x == null)

    fun <T : String> genericWithConstraint(x: T): T = x

    fun <T> genericWithMultipleConstraints(x: T): T
            where T : Comparable<T>,
                  T : SomeExternalInterface,
                  T : Throwable = x

    fun <A, B, C, D, E> generic3(a: A, b: B, c: C, d: D): E? = null

    inline fun inlineFun(x: Int, callback: (Int) -> Unit) {
        callback(x)
    }
}