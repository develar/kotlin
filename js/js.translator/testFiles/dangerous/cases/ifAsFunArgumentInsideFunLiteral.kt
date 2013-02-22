package foo

fun box(): Boolean {
    val d = {
        val host = "a"
        val connectDisposable = "d"
        sum(if (host == "localhost") "127.0.0.1" else host, connectDisposable)
    }
    return true
}

fun sum(a: String, b: String) {
}