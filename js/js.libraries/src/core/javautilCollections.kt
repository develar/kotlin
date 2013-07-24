package java.util.Collections

import java.lang.*
import java.util.*

native("collectionsMax", "kotlin")
public fun max<T>(col: Collection<T>, comp: Comparator<T>): T

native("collectionsSort", "Kotlin")
public fun <T> sort(list: MutableList<T>): Unit

native("collectionsSort", "Kotlin")
public fun <T> sort(list: MutableList<T>, comparator: java.util.Comparator<T>): Unit