package js

native
public val noImpl: Nothing = throw Exception()

/** Provides [] access to maps */
native public fun <K, V> MutableMap<K, V>.set(key: K, value: V): Unit = noImpl

public fun println(): Unit
public fun println(s: Any?): Unit
public fun print(s: Any?): Unit
//TODO: consistent parseInt
native public fun parseInt(s: String, radix: Int = 10): Int = noImpl
library
public fun safeParseInt(s: String): Int? = noImpl
library
public fun safeParseDouble(s: String): Double? = noImpl