package org.requirejs

public fun define(name: String, dependencies: Array<String>, functionDefinition: ()->Any): Unit
public fun require(name: String): Any