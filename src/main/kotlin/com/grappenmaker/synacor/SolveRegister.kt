package com.grappenmaker.synacor

import kotlin.system.measureTimeMillis

/*
Compiled code:
    178b    jt      R0      1793
    178e    add     R0      R1      0001
    1792    ret
    1793    jt      R1      17a0
    1796    add     R0      R0      7fff
    179a    set     R1      R7
    179d    call    178b
    179f    ret
    17a0    push    R0
    17a2    add     R1      R1      7fff
    17a6    call    178b
    17a8    set     R1      R0
    17ab    pop     R0
    17ad    add     R0      R0      7fff
    17b1    call    178b
    17b3    ret

Explanation:
    start
    R0 > 0 then
      R1 > 0 -> store old R0 on stack
                R1 -= 1
                -> recurse start
                R1 = R0
                reset old R0
                R0 -= 1
                -> recurse start
      R1 == 0 -> R0 -= 1
                 R1 = R7 (magic)
                 -> return recurse start
    R0 == 0 then return R0 = R1 + 1

(not very clear but the implementation makes it clear)

R0 is initially set to 4, R1 to 1.
Call site:
    156b    set     R0      0004
    156e    set     R1      0001
    1571    call    178b
    1573    eq      R1      R0      0006
    1577    jf      R1      15cb

Explanation:
if (deepRecursion(4, 1, R7) == 6) teleport()

Now to force it to work, do the following things:
set 5489 21
set 5490 21
set 5491 21
set 5492 1
set 5493 32768
set 5494 1
set R7 25734 (output of main, in my case this value)
 */

// We can pack these since we know they are at most 15 bits, which fits in an int
// Inline for speed
@Suppress("NOTHING_TO_INLINE")
private inline fun pack(reg0: Int, reg1: Int) = reg0 shl 15 or reg1

// Great naming btw
// TODO: optimize more?
fun MutableMap<Int, Int>.deepRecursion(reg0: Int, reg1: Int, reg7: Int): Int = getOrPut(pack(reg0, reg1)) {
    when {
        reg0 == 0 -> (reg1 + 1).mod(0x8000)
        reg1 == 0 -> deepRecursion(reg0 - 1, reg7, reg7)
        else -> deepRecursion(reg0 - 1, deepRecursion(reg0, reg1 - 1, reg7), reg7)
    }
}

fun main() = println("Took ${
    measureTimeMillis {
        for (i in 0 until 0x8000) {
            if (i % 1000 == 0) println(i)

            val cache = hashMapOf<Int, Int>()
            if (cache.deepRecursion(4, 1, i) == 6) {
                println("Found R7=$i!")
                break
            }
        }
    }
}ms")