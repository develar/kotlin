package java.lang

library("Error")
open public class Exception(message: jet.String? = null): Throwable() {
    public val stack: jet.String
}

library
public class IllegalArgumentException(message: jet.String? = null): Exception()

library
public class IllegalStateException(message: jet.String? = null): Exception()

library("RangeError")
native public class IndexOutOfBoundsException(message: jet.String? = null): Exception(message)

library
public class UnsupportedOperationException(message: jet.String? = null): Exception()

library
public class NumberFormatException(message: jet.String? = null): Exception()

library
public trait Runnable {
    public open fun run(): Unit;
}

library
public trait Comparable<T> {
    public fun compareTo(that: T): Int
}

native public trait Appendable {
    public fun append(csq: jet.CharSequence?): Appendable?
    public fun append(csq: jet.CharSequence?, start: Int, end: Int): Appendable?
    public fun append(c: Char): Appendable?
}