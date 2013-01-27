package foo

native fun console_log(vararg message: Any): String = noImpl
native fun paramCount(vararg a : Int) : Int = noImpl

fun count(vararg a : Int) = a.size

fun log(vararg message: Any) = console_log(message)

fun box() : Boolean {
    if (paramCount(1, 2 ,3) != 3) {
        return false;
    }
    if (paramCount() != 0) {
        return false;
    }
    if (count() != 0) {
        return false;
    }
    if (count(1, 1, 1, 1) != 4) {
        return false;
    }
    return log("foo", "bar") == "foo,bar";
}