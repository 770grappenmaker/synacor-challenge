package com.grappenmaker.synacor

import java.io.File

fun Int.hex() = "%04x".format(this)

fun File.asProgram() = readBytes().asProgram()

fun ByteArray.asProgram() =
    toList().windowed(2, 2) { (lo, hi) -> (hi.toInt() and 0xff shl 8) or (lo.toInt() and 0xff) }.toIntArray()

fun IntArray.toBytes() = toList().flatMap { listOf((it and 0xFF).toByte(), (it shr 8 and 0xFF).toByte()) }.toByteArray()

fun Boolean.toInt() = if (this) 1 else 0
fun Int.ranged() = mod(0x8000)