package foo

trait Foo {
    fun execute(): Boolean {
        return execute(false)
    }

    fun execute(use: Boolean): Boolean
}

object foo : Foo {
    override fun execute(use: Boolean) = true
}

fun box(): Boolean {
    return foo.execute()
}