// WITH_STDLIB
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses, +GenericInlineClassParameter

OPTIONAL_JVM_INLINE_ANNOTATION
value class Z1<T: Int?>(val x: T)

OPTIONAL_JVM_INLINE_ANNOTATION
value class Z2<T: Z1<Int?>>(val z: T)

OPTIONAL_JVM_INLINE_ANNOTATION
value class ZN<T: Z1<Int?>?>(val z: T)

fun wrap1(n: Int): Z1<Int?>? = if (n < 0) null else Z1(n)
fun wrap2(n: Int): Z2<Z1<Int?>>? = if (n < 0) null else Z2(Z1(n))
fun wrapN(n: Int): ZN<Z1<Int?>?>? = if (n < 0) null else ZN(Z1(n))

fun box(): String {
    if (wrap1(-1) != null) throw AssertionError()
    if (wrap1(42) == null) throw AssertionError()
    if (wrap1(42)!!.x != 42) throw AssertionError()

    if (wrap2(-1) != null) throw AssertionError()
    if (wrap2(42) == null) throw AssertionError()
    if (wrap2(42)!!.z.x != 42) throw AssertionError()

    if (wrapN(-1) != null) throw AssertionError()
    if (wrapN(42) == null) throw AssertionError()
    if (wrapN(42)!!.z!!.x != 42) throw AssertionError()

    return "OK"
}