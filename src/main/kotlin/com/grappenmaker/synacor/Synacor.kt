@file:JvmName("Synacor")

package com.grappenmaker.synacor

import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun main(args: Array<String>) = with(Computer()) {
    println("Welcome to the Synacor Challenge interpreter. Type /help for help. Load a program with /load")

    args.firstOrNull()?.let { bootProgram(File(it).asProgram()) }
    runUntilHalt()

    log("Computer halted, ending execution")
}

class Computer {
    private val resettables = mutableListOf<Resettable<*>>()
    private fun <T> resettable(resetter: () -> T): ReadWriteProperty<Computer, T> =
        Resettable(resetter).also { resettables += it }

    val memory by resettable { IntArray(0x8000) }
    val registers by resettable { IntArray(8) }
    val stack by resettable { ArrayDeque<Int>() }
    var pc by resettable { 0 }
    var running by resettable { false }
    var paused by resettable { true }

    val inputCharacters by resettable { mutableListOf<Char>() }
    val executedCommands by resettable { mutableListOf<String>() }

    operator fun get(index: Int) = when (index) {
        in 0..0x7fff -> memory[index]
        in 0x8000..0x8007 -> registers[index.ranged()]
        else -> error("Invalid memory index $index")
    } and 0xFFFF

    operator fun set(index: Int, value: Int) = when (index) {
        in 0..0x7fff -> memory[index] = value and 0xFFFF
        in 0x8000..0x8007 -> registers[index.ranged()] = value and 0xFFFF
        else -> error("Invalid memory index $index")
    }

    fun valued(index: Int) = when (index) {
        in 0..0x7fff -> index
        in 0x8000..0x8007 -> registers[index.ranged()]
        else -> error("Invalid memory index $index")
    } and 0xFFFF

    private fun currOffset(index: Int) = get(pc + index + 1)
    fun parameter(index: Int) = valued(currOffset(index))
    fun respond(value: Int) = set(currOffset(0), value)

    fun step() {
        val opcode = memory[pc]
        with(Opcodes[opcode] ?: error("Invalid opcode $opcode")) { handle() }
    }

    fun halt() {
        running = false
    }

    fun runUntilHalt() {
        running = true
        while (running) when {
            paused -> handleDebug((askInput() ?: return halt()).removePrefix("/"))
            else -> step()
        }
    }

    // reset logic
    class Resettable<T>(private val resetter: () -> T) : ReadWriteProperty<Computer, T> {
        private var underlying = resetter()

        override fun getValue(thisRef: Computer, property: KProperty<*>) = underlying
        override fun setValue(thisRef: Computer, property: KProperty<*>, value: T) {
            underlying = value
        }

        fun reset() {
            underlying = resetter()
        }
    }

    fun reset() = resettables.forEach { it.reset() }

    private val whitespace = """\s+""".toRegex()
    private fun handleDebug(command: String) {
        val partialArgs = command.split(whitespace)
        val name = partialArgs.first()

        val cmd = DebugCommands[name] ?: return log("Unknown command $name, try /help")
        if (cmd.requiresPause && !paused) return log("The computer is not paused! Pause it first with /pause")
        cmd.handler(this, partialArgs.drop(1))
    }

    private fun askInput(): String? {
        print("\$ ")
        return readlnOrNull()
    }

    fun getInputChar(): Int? {
        while (inputCharacters.isEmpty()) {
            val line = askInput() ?: return null
            if (line.startsWith('/')) {
                handleDebug(line.drop(1))
                continue
            }

            executedCommands += line
            inputCharacters += (line + '\n').toList()
        }

        return inputCharacters.removeFirst().code
    }

    fun bootProgram(program: IntArray) {
        reset()
        program.copyInto(memory)
        running = true

        log("Loaded program of size ${program.size}, start execution with /pause")
    }

    fun log(message: String) = println("[Interpreter] $message")
}

