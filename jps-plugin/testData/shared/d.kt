package test.d

import test.a.A
import test.c.C

public class D() {
    fun d(): Array<Array<String>> = array(C().c(), A().a())
}