package js.stdlib

public trait Iterator<T> {
    public fun next(): T
    public fun hasNext(): Boolean
}