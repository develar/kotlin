package foo

fun setTimeout(callback: ()->Unit) = callback()

fun box(): Boolean {
    fun send() {
        setTimeout {
            send()
        }
    }
    return true
}