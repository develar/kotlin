// "Add '<*, *>'" "false"
// ERROR: Type argument expected
class C2<T>

fun foo<T>() {}

fun test() {
    foo<C2<caret>>()
}