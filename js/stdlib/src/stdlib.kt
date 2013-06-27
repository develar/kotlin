package js.stdlib

trait Iterator<T> {
    fun hasNext(): Boolean
    fun next(): T
}

class ArrayIterator<T>(private val array: Array<T>) : Iterator<T> {
    private val size: Int = array.size
    private var index = 0

    override fun hasNext() = index < size

    override fun next() = array[index++]
}

fun <T> arrayIterator(array:Array<T>) = ArrayIterator(array)

trait Runnable {
    fun run()
}

trait Comparable<in T> {
    fun compareTo(other: T): Int
}

trait Closeable {
    fun close()
}

trait Comparator<T> {
    fun compare(obj1: T, obj2: T): Int
}

fun <T> comparator(comparator: (obj1: T, obj2: T)->Int): Comparator<T> = object : Comparator<T> {
    override fun compare(obj1: T, obj2: T) = comparator(obj1, obj2)
}

class RangeIterator(val start: Int, val end: Int, private val increment: Int): Iterator<Int> {
    var next = 0

    override fun hasNext() = if (increment > 0) next <= end else next >= end

    override fun next(): Int {
        val current = next
        next += increment
        return current
    }
}

class NumberRange(val start: Int, val end: Int) {
    val increment: Int
        get() = 1

    fun contains(number: Int) = start <= number && number <= end;

    fun iterator() = RangeIterator(start, end, increment)
}

trait Iterable<T> {
    fun iterator(): Iterator<T>
}

class NumberProgression(val start: Int, val end: Int, val increment: Int) : Iterable<Int> {
    override fun iterator() = RangeIterator(start, end, increment)
}

fun <T> arrayIndices(array:Array<T>) = NumberRange(0, array.size - 1)

fun upTo(from: Int, to: Int) = NumberRange(from, to)

fun downTo(from: Int, to: Int) = NumberProgression(from, to, -1)