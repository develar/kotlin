package foo

class Foo(val postfix: String) {
    public fun invoke(text: String): String {
        return text + postfix
    }
}

fun box() : String {
    // todo KT-2515
    //val a = Foo(" world!")
    //return a("hello")
    var callback = {}
    callback.invoke()
    return "hello world!"
}