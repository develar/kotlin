package js

public val noImpl: Nothing

/** Provides [] access to maps */
public fun <K, V> MutableMap<K, V>.set(key: K, value: V): Unit

public fun println(): Unit
public fun println(s: Any?): Unit
public fun print(s: Any?): Unit
//TODO: consistent parseInt
public fun parseInt(s: String, radix: Int = 10): Int

public native(qualifier = "Kotlin") fun safeParseInt(s: String): Int?
public native(qualifier = "Kotlin") fun safeParseDouble(s: String): Double?