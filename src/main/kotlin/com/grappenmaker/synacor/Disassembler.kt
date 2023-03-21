package com.grappenmaker.synacor

fun Int.readable(char: Boolean = false) = when {
    char -> toInt().toChar().toString()
    this in 0..0x7fff -> hex()
    this in 0x8000..0x8007 -> "R${ranged()}"
    else -> error("Invalid memory $this")
}

fun String.asMemLocation() = when {
    startsWith('R') -> drop(1).toInt() + 0x8000
    else -> toInt(16)
}

const val space = 6
const val total = 4 + space

fun String.pad() = padEnd(total)

fun IntArray.disassemble(part: IntRange = indices) = buildString {
    require(indices.first >= 0 && indices.last < this@disassemble.size) { "part out of range" }

    var ptr = part.first
    while (ptr in part) {
        append(ptr.hex().pad())

        val curr = this@disassemble[ptr]
        val op = Opcodes[curr]

        if (op != null && ptr + op.parameters in part) {
            append(op.mnemonic.pad())
            repeat(op.parameters) { append(this@disassemble[++ptr].readable(op.mnemonic == "out").pad()) }
        } else {
            append("dat".pad() + curr.readable().pad())
            if (curr in 0..255) append("(${curr.readable(true)})")
        }

        appendLine()
        ptr++
    }
}

fun IntArray.strings() = buildString {
    var curr = ""
    for ((idx, b) in this@strings.withIndex()) {
        val char = b.toChar()
        when {
            b in 0..255 && !char.isISOControl() -> curr += char
            curr.isNotEmpty() -> {
                appendLine("${(idx - curr.length).hex().pad()} $curr")
                curr = ""
            }
        }
    }
}