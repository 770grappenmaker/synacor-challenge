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
    /set 1571 15
    /set 1572 15
    /set 1573 15
    /set 1574 1
    /set 1575 8000
    /set 1576 1
    /set R7 6486 (output of main, in hex, in my case this value)
Or use my handy-dandy command, /teleporter
 */

fun MutableMap<Int, Int>.deepRecursion(reg0: Int, reg1: Int, reg7: Int): Int = getOrPut(reg0 shl 15 or reg1) {
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