package com.grappenmaker.synacor

fun main() {
    fun List<Int>.eval() = get(0) + get(1) * (get(2) * get(2)) + (get(3) * get(3) * get(3)) - get(4)

    fun solve(toPick: List<Int>, curr: List<Int>): List<Int>? = when {
        toPick.isEmpty() -> if (curr.eval() == 399) curr else null
        else -> toPick.firstNotNullOfOrNull { solve(toPick - it, curr + it) }
    }

    println(solve(listOf(2, 3, 5, 7, 9), emptyList()))
}