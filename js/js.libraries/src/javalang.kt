package java.lang

open native("Error") public class Exception(message: jet.String? = null): Throwable() {
    public val stack: jet.String
}

open public class RuntimeException(message: jet.String? = null) : Exception(message)
public class IllegalArgumentException(message: jet.String? = null): Exception()
public class IllegalStateException(message: jet.String? = null): Exception()
public class UnsupportedOperationException(message: jet.String? = null): Exception()
public class NumberFormatException(message: jet.String? = null): Exception()

public native("RangeError") class IndexOutOfBoundsException(message: jet.String? = null): Exception(message)

public native(qualifier = "Kotlin") trait Runnable {
    public open fun run(): Unit;
}

public native(qualifier = "Kotlin") trait Comparable<T> {
    public fun compareTo(that: T): Int
}

public trait Appendable {
    public fun append(csq: jet.CharSequence?): Appendable?
    public fun append(csq: jet.CharSequence?, start: Int, end: Int): Appendable?
    public fun append(c: Char): Appendable?
}