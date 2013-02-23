package foo

abstract class TabService {
    private var foo = "a"
    abstract fun reload(bypassCache: String = foo): String
}

class FirefoxTabService : TabService() {
    override fun reload(bypassCache: String) = bypassCache
}

fun box() = FirefoxTabService().reload() == "a"

