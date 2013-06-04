package js

public open class Json() {
    public fun <T> get(propertyName: String): T
    public fun set(propertyName: String, value: Any?): Unit
}

//library("jsonFromTuples")
//public fun json(vararg pairs: Pair<String, Any?>): Json = js.noImpl

//library("jsonFromTuples")
//public fun json2(pairs: Array<Pair<String, Any?>>): Json = js.noImpl

public native("jsonAddProperties", "Kotlin") fun Json.add(other: Json): Json

public object JSON {
    public fun stringify(o: Any?): String

    public fun stringify(o: Any?, replacer: ((key: String, value: Any?)->Unit)?, space: Int? = null): String

    public fun stringify(o: Any?, replacer: ((key: String, value: Any?)->Unit)?, space: String? = null): String

    public fun stringify(o: Any?, replacer: Array<String>?, space: Int?): String

    public fun stringify(o: Any?, replacer: Array<String>?, space: String? = null): String

    public fun parse<T>(text: String, reviver: ((key: String, value: Any?)->Unit)? = null): T
}

public trait JsonSerializable {
    public fun toJSON(): String
}