package foo

native
fun _setTimeout(callback: ()->Unit): Unit = noImpl

var done = false
var result = ""

object foo {
    var timeoutId: Long = 1

    val callbackWrapper = {
        timeoutId = -1 as Long
        done = true
    }
}

private class Bar {
    val s = "v"

    {
        _setTimeout {
            _setTimeout {
                result = s
            }
        }
    }
}

fun box(): Boolean {
    val bar = Bar()
    _setTimeout(foo.callbackWrapper)
    return foo.timeoutId == -1 as Long && done && result == bar.s
}