package com.grappenmaker.synacor

fun Int.hex() = "%04x".format(this)
fun UInt.hex() = "%04x".format(toInt())