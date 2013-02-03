package foo.bar

fun registerExtension(callback: ()->Unit) {

}

private val doInit = {
    registerExtension() {
    }
    true
}()

fun box() = doInit