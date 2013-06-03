package foo

optionsArg
native fun getCookies(url:String? = null, name:String? = null, storeId:String? = null, callback:(()->String)? = null):String = noImpl

fun box() = getCookies(url = "u") { "yep" } == "uyep"
