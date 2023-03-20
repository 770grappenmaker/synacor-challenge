package com.grappenmaker.synacor

fun UInt.readable(char: Boolean = false) = when {
    char -> toInt().toChar().toString()
    this in 0U..0x7fffU -> hex()
    this in 0x8000U..0x8007U -> "R${ranged()}"
    else -> error("Invalid memory $this")
}

fun String.asMemLocation() = when {
    startsWith('R') -> drop(1).toUInt() + 0x8000U
    else -> toUInt()
}

const val space = 6
const val total = 4 + space

fun String.pad() = padEnd(total)

fun UIntArray.disassemble() = buildString {
    var ptr = 0
    while (ptr < this@disassemble.size) {
        append(ptr.hex().pad())

        val curr = this@disassemble[ptr]
        val op = Opcodes[curr]

        if (op != null) {
            append(op.mnemonic.pad())
            repeat(op.parameters) { append(this@disassemble[++ptr].readable(op.mnemonic == "out").pad()) }
        } else {
            append("dat".pad() + curr.readable().pad())
            if (curr in 0U..255U) append("(${curr.readable(true)})")
        }

        appendLine()
        ptr++
    }
}

fun UIntArray.strings() = buildString {
    var curr = ""
    for ((idx, b) in this@strings.withIndex()) {
        val char = b.toInt().toChar()
        when {
            b in 0U..255U && !char.isISOControl() -> curr += char
            curr.isNotEmpty() -> {
                appendLine("${(idx - curr.length).hex().pad()} $curr")
                curr = ""
            }
        }
    }
}