// This file was generated automatically. See  generateTestDataForTypeScriptWithFileExport.kt
// DO NOT MODIFY IT MANUALLY.

// CHECK_TYPESCRIPT_DECLARATIONS
// SKIP_NODE_JS
// INFER_MAIN_MODULE
// MODULE: JS_TESTS
// MODULE_KIND: ES
// FILE: esm.kt

@file:JsExport

package foo


val value = 10


var variable = 10


class C(val x: Int) {
    fun doubleX() = x * 2
}


object O {
    val value = 10
}


object Parent {
    val value = 10
    class Nested {
        val value = 10
    }
}


fun box(): String = "OK"