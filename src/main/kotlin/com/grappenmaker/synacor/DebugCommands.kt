package com.grappenmaker.synacor

import java.io.File

object DebugCommands {
    private val commands: Map<String, CommandInfo> = buildMap {
        command(
            name = "help",
            description = "Shows this help"
        ) {
            println("Available debug commands:")
            entries.sortedBy { it.key }.forEach { (name, info) -> println("/$name: ${info.description}") }

            println()
            println("If it makes sense, the interpreter expects a hex value, instead of decimal")
            println()
        }

        dumpCommand(
            name = "dump",
            description = "Dumps the current memory to disk",
            file = File("dump.bin")
        ) { memory.toBytes() }

        dumpCommand(
            name = "dumpdisassembly",
            description = "Disassembles the current memory, and writes it to disk",
            file = File("disassembled.txt")
        ) { memory.disassemble().encodeToByteArray() }

        dumpCommand(
            name = "strings",
            description = "Dumps all currently loaded ASCII sequences",
            file = File("strings.txt")
        ) { memory.strings().encodeToByteArray() }

        fun Computer.inspect() = log(
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
            log("Step inspection is now ${if (stepInspect) "on" else "off"}")
        }

        fun Computer.stepInspect() {
            step()
            if (stepInspect) inspect()
        }

        command(name = "pause", description = "(Un)pauses the computer") {
            paused = !paused
            log("The computer is now ${if (!paused) "un" else ""}paused")
            if (paused) log("Since the computer still asks for input, the next input will still be passed on to the computer")
        }

        command(name = "step", description = "Steps the computer (when paused)", requiresPause = true) { stepInspect() }

        command(name = "stepout", description = "Steps until output", requiresPause = true) {
            while (memory[pc] != 19) stepInspect()
        }

        command(name = "stepnewline", description = "Steps until newline", requiresPause = true) {
            while (memory[pc] != 19 || parameter(0) != 10) stepInspect()
        }

        command(name = "inspect", description = "Inspects some useful information") { inspect() }
        command(name = "set", description = "Sets a value in memory. Usage: <address> <value>") { args ->
            if (args.size != 2) return@command log("Invalid arguments")
            runCatching { this[args.first().asMemLocation()] = args[1].toInt(16) }
                .onFailure { log("Failed to set mem:"); it.printStackTrace() }
        }

        command(name = "savecommands", description = "Saves commands to a given file") { args ->
            val file = File(args.firstOrNull() ?: return@command log("Specify a command file!"))
            file.writeText(executedCommands.joinToString(System.lineSeparator()))
        }

        command(name = "teleporter", description = "Automatically uses (hacks) the teleporter") {
            memory[5489] = 21
            memory[5490] = 21
            memory[5491] = 21
            memory[5492] = 1
            memory[5493] = 32768
            memory[5494] = 1
            registers[7] = 25734
            inputCharacters += "use teleporter\n".toList()
        }

        command(
            name = "eval",
            description = "Evaluates commands from a command file (does not include slash-commands)"
        ) { args ->
            val file = File(args.firstOrNull() ?: return@command log("Specify a command file!"))
            if (!file.exists()) return@command log("File $file does not exist!")

            inputCharacters += file.readLines().filterNot { it.startsWith("//") }
                .joinToString(separator = "\n", postfix = "\n").toList()
        }

        command(name = "jump", description = "Unconditionally jumps/sets pc to given address") { args ->
            pc = args.firstOrNull()?.toIntOrNull(16) ?: return@command log("Invalid PC")
        }

        command(name = "load", description = "Loads a binary into memory (at address 0) and resets") { args ->
            val file = File(args.firstOrNull() ?: return@command log("Specify a path!"))
            if (!file.exists()) return@command log("File $file doesn't exist!")

            bootProgram(file.asProgram())
        }

        command(
            name = "disassemble",
            description = "Disassembles and prints a range of memory. Usage: <from> <to> (to is inclusive)"
        ) { args ->
            val from = args.getOrNull(0)?.toIntOrNull(16) ?: return@command log("Specify a valid from!")
            val to = args.getOrNull(1)?.toIntOrNull(16) ?: return@command log("Specify a valid to!")

            log("Disassembly of ${from.hex()}..${to.hex()}:")
            println(memory.disassemble(from..to))
        }
    }

    private fun MutableMap<String, CommandInfo>.dumpCommand(
        name: String,
        description: String,
        file: File,
        task: Computer.() -> ByteArray
    ) = put(name, CommandInfo(description) {
        file.writeBytes(task())
        log("Written to $file")
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