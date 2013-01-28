package foo

native fun console_log(vararg message: Any): String = noImpl
native fun paramCount(vararg a: Int): Int = noImpl

fun count(vararg a: Int) = a.size

fun debug(vararg message: Any) = console_log(message)
fun info(message: String) = console_log(message)

fun box(): Boolean {
    if (paramCount(1, 2, 3) != 3) {
        return false
    }
    if (paramCount() != 0) {
        return false
    }
    if (count() != 0) {
        return false
    }
    if (count(1, 1, 1, 1) != 4) {
        return false
    }
    if (debug("foo", "bar") != "foo,bar") {
        return false
    }
    if (info("foo") != "foo") {
        return false
    }
    return true
}