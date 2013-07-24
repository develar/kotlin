package java.util

import java.lang.*

public native("collectionsMax", "kotlin") fun max<T>(col: jet.Collection<T>, comp: Comparator<T>): T

public native(qualifier = "Kotlin") trait Comparator<T> {
    fun compare(obj1: T, obj2: T): Int;
}

public abstract class AbstractCollection<E>(): MutableCollection<E> {
    override fun toArray(): Array<Any?>
    override fun <T> toArray(a: Array<out T>): Array<T>

    override fun isEmpty(): Boolean
    override fun contains(o: Any?): Boolean
    override fun iterator(): MutableIterator<E>

    override fun add(e: E): Boolean
    override fun remove(o: Any?): Boolean

    override fun addAll(c: jet.Collection<E>): Boolean
    override fun containsAll(c: jet.Collection<Any?>): Boolean
    override fun removeAll(c: jet.Collection<Any?>): Boolean
    override fun retainAll(c: jet.Collection<Any?>): Boolean

    override fun clear(): Unit
    override fun size(): Int

    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
}

public abstract class AbstractList<E>(): AbstractCollection<E>(), MutableList<E> {
    override fun get(index: Int): E
    override fun set(index: Int, element: E): E

    override fun add(e: E): Boolean
    override fun add(index: Int, element: E): Unit
    override fun addAll(index: Int, c: jet.Collection<E>): Boolean

    override fun remove(index: Int): E

    override fun indexOf(o: Any?): Int
    override fun lastIndexOf(o: Any?): Int

    override fun listIterator(): MutableListIterator<E>
    override fun listIterator(index: Int): MutableListIterator<E>

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E>

    override fun equals(other: Any?): Boolean

    fun toString(): String
    override fun size(): Int
}

public class ArrayList<E>(capacity: Int = 0): AbstractList<E>() {
}

// JS array is sparse, so, there is no any difference between ArrayList and LinkedList
public class LinkedList<E>(): AbstractList<E>() {
    public fun poll(): E
    public fun peek(): E
    public fun offer(e: E): Boolean
}

public native(qualifier = "Kotlin") class HashSet<E>(): AbstractCollection<E>(), MutableSet<E> {
}

public native(qualifier = "Kotlin") trait SortedSet<E>: Set<E> {
}

public open native(qualifier = "Kotlin") class TreeSet<E>(): AbstractCollection<E>(), MutableSet<E>, SortedSet<E> {
}

public open native(qualifier = "Kotlin") class LinkedHashSet<E>(): AbstractCollection<E>(), MutableSet<E> {
}

public open class HashMap<K, V>(capacity: Int = 0): MutableMap<K, V> {
    override public fun size(): Int
    override public fun isEmpty(): Boolean
    override public fun get(key: Any?): V?
    override public fun containsKey(key: Any?): Boolean
    override public fun put(key: K, value: V): V
    override public fun putAll(m: jet.Map<out K, V>): Unit
    override public fun remove(key: Any?): V?
    override public fun clear(): Unit
    override public fun containsValue(value: Any?): Boolean
    override public fun keySet(): MutableSet<K>
    override public fun values(): MutableCollection<V>
    override public fun entrySet(): MutableSet<MutableMap.MutableEntry<K, V>>
}

public class StringBuilder(): Appendable {
    override fun append(c: Char): Appendable?
    override fun append(csq: CharSequence?): Appendable?
    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable?
    public fun append(obj: Any?): StringBuilder
    public fun toString(): String
}

public class NoSuchElementException(): Exception() {
}

public native(qualifier = "Kotlin") trait Enumeration<E> {
    open public fun hasMoreElements(): Boolean
    open public fun nextElement(): E
}