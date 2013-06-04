package kotlin

import java.util.*

public native("comparator", "Kotlin") fun comparator<T>(f: (T, T) -> Int): Comparator<T>

public fun <T> array(vararg value: T): Array<T>

// "constructors" for primitive types array
public fun doubleArray(vararg content: Double): DoubleArray

public fun floatArray(vararg content: Float): FloatArray

public fun longArray(vararg content: Long): LongArray

public fun intArray(vararg content: Int): IntArray

public fun charArray(vararg content: Char): CharArray

public fun shortArray(vararg content: Short): ShortArray

public fun byteArray(vararg content: Byte): ByteArray

public fun booleanArray(vararg content: Boolean): BooleanArray

