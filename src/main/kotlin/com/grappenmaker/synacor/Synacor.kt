@file:JvmName("Synacor")

package com.grappenmaker.synacor

import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// TODO: kotlin's unsigned types aren't that great
fun main() {
    val program = File("challenge.bin").readBytes().loadProgram()
    println("Loaded program of size ${program.size}")
    println()

    with(Computer()) {
        loadProgram(program)
        runUntilHalt()
        println("Computer halted, ending execution")
    }
}

fun ByteArray.loadProgram() = toList().windowed(2, 2) { (lo, hi) ->
    ((hi.toInt() and 0xff shl 8) or (lo.toInt() and 0xff)).toUInt()
}.toUIntArray()

fun UIntArray.toBytes() = toList()
    .flatMap { listOf((it.toInt() and 0xFF).toByte(), (it.toInt() shr 8 and 0xFF).toByte()) }.toByteArray()

fun Boolean.toUInt() = if (this) 1U else 0U
fun UInt.ranged() = mod(0x8000U)

object Opcodes {
    private val supportedOpcodes = buildMap {
        opcode("halt", 0U) { halt() }

        opcode("set", 1U, parameters = 2) { respond(valuedParameter(1)) }
        opcode("push", 2U, parameters = 1) { stack.addLast(valuedParameter(0)) }
        opcode("pop", 3U, parameters = 1) { respond(stack.removeLast()) }

        opcode("eq", 4U, parameters = 3) { respond((valuedParameter(1) == valuedParameter(2)).toUInt()) }
        opcode("gt", 5U, parameters = 3) { respond((valuedParameter(1) > valuedParameter(2)).toUInt()) }

        jumpOpcode("jmp", 6U)
        jumpOpcode("jt", 7U, parameters = 2) { valued(parameter(0)) != 0U }
        jumpOpcode("jf", 8U, parameters = 2) { valued(parameter(0)) == 0U }

        opcode("add", 9U, parameters = 3) { respond((valuedParameter(1) + valuedParameter(2)).ranged()) }
        opcode("mul", 10U, parameters = 3) { respond((valuedParameter(1) * valuedParameter(2)).ranged()) }
        opcode("mod", 11U, parameters = 3) { respond((valuedParameter(1) % valuedParameter(2)).ranged()) }
        opcode("and", 12U, parameters = 3) { respond(valuedParameter(1) and valuedParameter(2)) }
        opcode("or", 13U, parameters = 3) { respond(valuedParameter(1) or valuedParameter(2)) }
        opcode("not", 14U, parameters = 2) { respond(valuedParameter(1).inv() and 0x7FFFU) }

        opcode("rmem", 15U, parameters = 2) { respond(this[valuedParameter(1)]) }
        opcode("wmem", 16U, parameters = 2) { this[valuedParameter(0)] = valuedParameter(1) }

        opcode("call", 17U, parameters = 1, updatePC = false) {
            stack.addLast(pc + 2U)
            pc = valuedParameter(0)
        }

        opcode("ret", 18U, updatePC = false) { pc = (stack.removeLastOrNull() ?: return@opcode halt()) }

        opcode("out", 19U, parameters = 1) { print(valuedParameter(0).toInt().toChar()) }
        opcode("in", 20U, parameters = 1) { respond(getInputChar() ?: return@opcode halt()) }

        opcode("noop", 21U) { /* no-op */ }
    }

    private fun MutableMap<UInt, Opcode>.opcode(
        mnemonic: String,
        code: UInt,
        parameters: Int = 0,
        updatePC: Boolean = true,
        handler: Computer.() -> Unit
    ) = put(code, SimpleOpcode(mnemonic, parameters, updatePC, handler))

    private fun MutableMap<UInt, Opcode>.jumpOpcode(
        mnemonic: String,
        code: UInt,
        parameters: Int = 1,
        cond: Computer.() -> Boolean = { true }
    ) = put(code, JumpOpcode(mnemonic, parameters, cond))

    private class SimpleOpcode(
        override val mnemonic: String,
        override val parameters: Int,
        val updatePC: Boolean,
        val handler: Computer.() -> Unit
    ) : Opcode {
        override fun Computer.handle() {
            handler()
            if (updatePC) pc += parameters.toUInt() + 1U
        }
    }

    private class JumpOpcode(
        override val mnemonic: String,
        override val parameters: Int,
        val cond: Computer.() -> Boolean
    ) : Opcode {
        override fun Computer.handle() {
            pc = if (cond()) valuedParameter(parameters - 1)
            else pc + parameters.toUInt() + 1U
        }
    }

    sealed interface Opcode {
        val mnemonic: String
        val parameters: Int

        fun Computer.handle()
    }

    operator fun get(op: UInt) = supportedOpcodes[op]
}

class Computer {
    private val resettables = mutableListOf<Resettable<*>>()
    private fun <T> resettable(resetter: () -> T): ReadWriteProperty<Computer, T> =
        Resettable(resetter).also { resettables += it }

    val memory by resettable { UIntArray(0x8000) }
    val registers by resettable { UIntArray(8) }
    val stack by resettable { ArrayDeque<UInt>() }
    var pc: UInt by resettable { 0U }
    var running by resettable { false }
        private set

    var paused by resettable { false }

    val inputCharacters by resettable { mutableListOf<Char>() }
    val executedCommands by resettable { mutableListOf<String>() }

    operator fun get(index: UInt) = when (index) {
        in 0U..0x7fffU -> memory[index.toInt()]
        in 0x8000U..0x8007U -> registers[index.ranged().toInt()]
        else -> error("Invalid memory index $index")
    } and 0xFFFFu

    operator fun set(index: UInt, value: UInt) = when (index) {
        in 0U..0x7fffU -> memory[index.toInt()] = value and 0xFFFFu
        in 0x8000U..0x8007U -> registers[index.ranged().toInt()] = value and 0xFFFFu
        else -> error("Invalid memory index $index")
    }

    fun valued(index: UInt) = when (index) {
        in 0U..0x7fffU -> index
        in 0x8000U..0x8007U -> registers[index.ranged().toInt()]
        else -> error("Invalid memory index $index")
    } and 0xFFFFu

    fun parameter(index: Int) = get(pc + index.toUInt() + 1U)
    fun valuedParameter(index: Int) = valued(parameter(index))
    fun respond(value: UInt) = set(parameter(0), value)

    fun step() {
        val opcode = memory[pc.toInt()]
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

    fun loadProgram(program: UIntArray) {
        reset()
        program.copyInto(memory)
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

        if (name.equals("help", ignoreCase = true)) {
            println("Available debug commands:")
            DebugCommands.commands.entries
                .sortedBy { it.key }.forEach { (name, info) -> println("/$name: ${info.description}") }

            println()
            return
        }

        val cmd = DebugCommands[name] ?: return println("Unknown command $name, try /help")
        if (cmd.requiresPause && !paused) return println("The computer is not paused! Pause it first with /pause")
        cmd.handler(this, partialArgs.drop(1))
    }

    private fun askInput(): String? {
        print("\$ ")
        return readlnOrNull()
    }

    fun getInputChar(): UInt? {
        while (inputCharacters.isEmpty()) {
            val line = askInput() ?: return null
            if (line.startsWith('/')) {
                handleDebug(line.drop(1))
                continue
            }

            executedCommands += line
            inputCharacters += (line + '\n').toList()
        }

        return inputCharacters.removeFirst().code.toUInt()
    }
}

object DebugCommands {
    val commands: Map<String, CommandInfo> = buildMap {
        command(
            name = "help",
            description = "Shows this help"
        ) { error("Fallen down into help?") }

        dumpCommand(
            name = "dump",
            description = "Dumps the current memory",
            file = File("dump.bin")
        ) { memory.toBytes() }

        dumpCommand(
            name = "disassemble",
            description = "Disassembles the current memory",
            file = File("disassembled.txt")
        ) { memory.disassemble().encodeToByteArray() }

        dumpCommand(
            name = "strings",
            description = "Dumps all currently loaded ASCII sequences",
            file = File("strings.txt")
        ) { memory.strings().encodeToByteArray() }

        fun Computer.inspect() = println(
            """
                Paused: $paused
                PC: ${pc.hex()}
                Stack: ${stack.map { it.hex() }}
                Registers: ${registers.toList().map { it.hex() }}
                
            """.trimIndent()
        )

        var stepInspect = false
        command(name = "stepinspection", description = "Toggles step inspection") {
            stepInspect = !stepInspect
            println("Step inspection is now ${if (stepInspect) "on" else "off"}")
        }

        fun Computer.stepInspect() {
            step()
            if (stepInspect) inspect()
        }

        command(name = "pause", description = "(Un)pauses the computer") {
            paused = !paused
            println("The computer is now ${if (!paused) "un" else ""}paused")
        }

        command(name = "step", description = "Steps the computer (when paused)", requiresPause = true) {
            step()
            stepInspect()
        }

        command(name = "stepout", description = "Steps until output", requiresPause = true) {
            while (memory[pc.toInt()] != 19U) {
                step()
                stepInspect()
            }
        }

        command(name = "stepnewline", description = "Steps until newline", requiresPause = true) {
            while (memory[pc.toInt()] != 19U || valuedParameter(0) != 10U) {
                step()
                stepInspect()
            }
        }

        command(name = "inspect", description = "Inspects some useful information") { inspect() }
        command(name = "set", description = "Sets a value in memory. Usage: <address> <value>") { args ->
            if (args.size != 2) return@command println("Invalid arguments")
            runCatching { this[args.first().asMemLocation()] = args[1].toUInt() }
                .onFailure { println("Failed to set mem:"); it.printStackTrace() }
        }

        command(name = "savecommands", description = "Saves commands to a given file") { args ->
            val file = File(args.firstOrNull() ?: return@command println("Specify a command file!"))
            file.writeText(executedCommands.joinToString(System.lineSeparator()))
        }

        command(name = "teleporter", description = "Automatically uses (hacks) the teleporter") {
            memory[5489] = 21U
            memory[5490] = 21U
            memory[5491] = 21U
            memory[5492] = 1U
            memory[5493] = 32768U
            memory[5494] = 1U
            registers[7] = 25734U
            inputCharacters += "use teleporter\n".toList()
        }

        command(
            name = "eval",
            description = "Evaluates commands from a command file (does not include slash-commands)"
        ) { args ->
            val file = File(args.firstOrNull() ?: return@command println("Specify a command file!"))
            if (!file.exists()) return@command println("File $file does not exist!")

            inputCharacters += file.readLines().filterNot { it.startsWith("//") }
                .joinToString(separator = "\n", postfix = "\n").toList()
        }
    }

    private fun MutableMap<String, CommandInfo>.dumpCommand(
        name: String,
        description: String,
        file: File,
        task: Computer.() -> ByteArray
    ) = put(name, CommandInfo(description) {
        file.writeBytes(task())
        println("Written to $file")
    })

    private fun MutableMap<String, CommandInfo>.command(
        name: String,
        description: String,
        requiresPause: Boolean = false,
        handler: Computer.(args: List<String>) -> Unit
    ) = put(name, CommandInfo(description, requiresPause, handler))

    data class CommandInfo(
        val description: String,
        val requiresPause: Boolean = false,
        val handler: Computer.(args: List<String>) -> Unit
    )

    operator fun get(name: String) = commands[name.lowercase()]
}