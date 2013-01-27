package foo

import html.console

fun testSize(expectedSize: Int, vararg i: Int): Boolean {
    return (i.size == expectedSize)
}

fun testSum(expectedSum: Int, vararg i: Int): Boolean {
    var sum = 0
    for (j in i) {
        sum += j
    }

    log(1, 2, 3)
    return (expectedSum == sum)
}

private fun log(vararg objects:Any?) = console.log(objects)

fun box() = testSize(0) && testSum(0) && testSize(3, 1, 1, 1) && testSum(3, 1, 1, 1) && testSize(6, 1, 1, 1, 2, 3, 4) && testSum(30, 10, 20, 0)