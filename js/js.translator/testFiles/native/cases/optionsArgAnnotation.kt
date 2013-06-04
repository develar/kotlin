package foo

native optionsArg fun getCookies(url: String? = null, name: String? = null, storeId: String? = null, callback: (()->String)? = null): String = noImpl
native optionsArg fun setTitle(optionsArg title: String, tabId: Int? = null): String = noImpl

native fun checkHeader(header: HttpHeader): String = noImpl

public native optionsArg class HttpHeader(public val name: String, public var value: String?)

fun box() = getCookies(url = "u") { "yep" } == "uyep" && setTitle("new", 3) == "new3" && setTitle("foo") == "foo" && checkHeader(HttpHeader("foo", "bar")) == "foobar"
