package foo

fun box(): Boolean {
    val bar = 33;
    val a = "No more bottles of beer on the wall, no more bottles of beer.\n" +
              "Go to the store and buy some more, ${bar} on the wall."

    val b = """
        "foo"
        'bar'
      """
    return true
}