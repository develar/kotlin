package java.io

public class IOException(message: String = ""): Exception() {
}

public native(qualifier = "Kotlin") trait Closeable {
    public open fun close(): Unit;
}
