package foo

fun setTimeout(callback: ()->Unit) = callback()

fun box(): Boolean {
    var bar = true
    fun send() {
        if (bar) {
            val f = 4;
        }
        setTimeout {
            if (bar) {
                bar = false
                send()
            }
        }
    }
    send()
    return !bar
}