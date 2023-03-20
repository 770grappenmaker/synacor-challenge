package com.grappenmaker.synacor

const val targetValue = 30

// probably overkill, I am a bit rusty though...
fun main() {
    with(
        """
            * 8 - 1
            4 * 11 *
            + 4 - 18
            22 - 9 *
        """.trimIndent().lines().map { it.split(" ").map(String::parseOrbItem) }.asGrid()
    ) {
        fun solve(path: List<Point> = listOf(bottomLeftCorner)) {
            val last = path.last()
            val value = path.map { this[it] }.getValue()
            if (value !in 1..0x7fff) return
            if (path.size > 15) return

            if (
                last == topRightCorner
                && path.size % 2 != 0
                && value == targetValue
            ) {
                println("Found *a* path: $path")
                println("Movement: ${path.windowed(2) { (a, b) -> (b - a).asDirection() } }")
                return
            } else {
                last.adjacentSides()
                    .filter { it != bottomLeftCorner && (it != topRightCorner || it !in path) }
                    .forEach { solve(path + it) }
            }
        }

        solve()
    }
}

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