package foo

trait TabService {
    fun reload(bypassCache: Boolean = true): Boolean
}

class FirefoxTabService : TabService {
    override fun reload(bypassCache: Boolean) = bypassCache
}

fun box() = FirefoxTabService().reload()

