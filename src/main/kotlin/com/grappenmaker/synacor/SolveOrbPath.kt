package com.grappenmaker.synacor

import kotlin.system.measureTimeMillis

const val targetValue = 30

// probably overkill, I am a bit rusty though...
fun main() = println(
    "Took ${
        measureTimeMillis {
            with(
                """
                * 8 - 1
                4 * 11 *
                + 4 - 18
                22 - 9 *
                """.trimIndent().lines().map { it.split(" ").map(String::parseOrbItem) }.asGrid()
            ) {
                val queue = queueOf<List<Point>>(listOf(bottomLeftCorner))
                queue.drain { path ->
                    val last = path.last()
                    val value = path.map { this[it] }.getValue()
                    when {
                        value !in 1..0x7fff || path.size > 15 -> return@drain
                        last == topRightCorner
                                && path.size % 2 != 0
                                && value == targetValue -> {
                            println("Found *a* path: $path")
                            println("Movement: ${path.windowed(2) { (a, b) -> (b - a).asDirection() }}")
                            return@with
                        }

                        else -> {
                            last.adjacentSides()
                                .filter { it != bottomLeftCorner && (it != topRightCorner || it !in path) }
                                .forEach { queue.addFirst(path + it) }
                        }
                    }
                }
            }
        }
    }ms"
)

fun String.parseOrbItem() = toIntOrNull()?.let { MazeItem.Number(it) } ?: MazeItem.Operator(
    when (this) {
        "*" -> Int::times
        "-" -> Int::minus
        "+" -> Int::plus
        else -> error("Illegal!")
    }
)

sealed interface MazeItem {
    data class Operator(val apply: (Int, Int) -> Int) : MazeItem

    @JvmInline
    value class Number(val value: Int) : MazeItem
}

// Assumes number - op - number - op etc.
fun List<MazeItem>.getValue() = drop(1).windowed(2, 2).fold((first() as MazeItem.Number).value) { acc, (a, b) ->
    (a as MazeItem.Operator).apply(acc, (b as MazeItem.Number).value)
}