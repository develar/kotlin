package test.a

public class A() {
    // scope must contains kotlin std lib symbols
    public fun a(): Array<String> = array("ff")
}

native fun doNotReportAboutMyParameters(p: String) = 42