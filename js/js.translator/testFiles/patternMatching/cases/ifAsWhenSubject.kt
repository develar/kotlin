package foo

fun box(): Boolean {
    val foo = 3
    when(if (foo == 3) return true else 4) {
        else -> return false
    }
}