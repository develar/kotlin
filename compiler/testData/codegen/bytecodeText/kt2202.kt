// KT-2202 Wrong instruction for invoke private setter

class A {
    private fun f1() {}

    fun foo() {
        f1()
    }
}

class B {
    var foo = 1
        private set

    fun foo() {
       foo = 2
    }
}

// 0 INVOKEVIRTUAL
// 4 INVOKESPECIAL
