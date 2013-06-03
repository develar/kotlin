package foo

native optionsArg fun getCookies(url:String? = null, name:String? = null, storeId:String? = null, callback:(()->String)? = null):String = noImpl
native optionsArg fun setTitle(optionsArg title:String, tabId:Int? = null):String = noImpl

fun box() = getCookies(url = "u") { "yep" } == "uyep" && setTitle("new", 3) == "new3" && setTitle("foo") == "foo"
