// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
class A {
    operator fun rangeUntil(other: A): Iterable<A> = TODO()
}

fun main(n: A, f: A) {
    for (i in f..<n) {

    }
}
