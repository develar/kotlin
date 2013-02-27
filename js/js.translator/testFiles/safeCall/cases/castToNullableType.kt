package foo

fun box(): Boolean {
    val a = null
    val s = a as Int?
    return s == null
}