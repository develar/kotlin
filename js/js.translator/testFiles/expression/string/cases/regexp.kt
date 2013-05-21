package foo

fun box(): Boolean {
    val queryOrFragmentRegExp = RegExp("(\\?|#|;).*$", "g")
    val queryOrFragmentInfo = queryOrFragmentRegExp.exec("d?foo")
    return queryOrFragmentInfo[0] == "?foo"
}