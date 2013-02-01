package foo

fun setTimeout(callback: ()->Unit) = callback()

fun box(): Boolean {
    fun send() {
        setTimeout {
            if (false) {
                send()
            }
        }
        return true
    }
    return true
}