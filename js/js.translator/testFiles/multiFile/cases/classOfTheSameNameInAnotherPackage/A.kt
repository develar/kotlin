package foo

import bar.B

open class A() {
    fun f() = 3
}

fun box() = (A().f() + B().f()) == 9