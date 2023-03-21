package com.grappenmaker.synacor

object Opcodes {
    private val supportedOpcodes = buildMap {
        opcode("halt", 0) { halt() }

        opcode("set", 1, parameters = 2) { respond(parameter(1)) }
        opcode("push", 2, parameters = 1) { stack.addLast(parameter(0)) }
        opcode("pop", 3, parameters = 1) { respond(stack.removeLast()) }

        opcode("eq", 4, parameters = 3) { respond((parameter(1) == parameter(2)).toInt()) }
        opcode("gt", 5, parameters = 3) { respond((parameter(1) > parameter(2)).toInt()) }

        jumpOpcode("jmp", 6)
        jumpOpcode("jt", 7, parameters = 2) { parameter(0) != 0 }
        jumpOpcode("jf", 8, parameters = 2) { parameter(0) == 0 }

        opcode("add", 9, parameters = 3) { respond((parameter(1) + parameter(2)).ranged()) }
        opcode("mul", 10, parameters = 3) { respond((parameter(1) * parameter(2)).ranged()) }
        opcode("mod", 11, parameters = 3) { respond((parameter(1) % parameter(2)).ranged()) }
        opcode("and", 12, parameters = 3) { respond(parameter(1) and parameter(2)) }
        opcode("or", 13, parameters = 3) { respond(parameter(1) or parameter(2)) }
        opcode("not", 14, parameters = 2) { respond(parameter(1).inv() and 0x7FFF) }

        opcode("rmem", 15, parameters = 2) { respond(this[parameter(1)]) }
        opcode("wmem", 16, parameters = 2) { this[parameter(0)] = parameter(1) }

        opcode("call", 17, parameters = 1, updatePC = false) {
            stack.addLast(pc + 2)
            pc = parameter(0)
        }

        opcode("ret", 18, updatePC = false) { pc = (stack.removeLastOrNull() ?: return@opcode halt()) }

        opcode("out", 19, parameters = 1) { print(parameter(0).toChar()) }
        opcode("in", 20, parameters = 1) { respond(getInputChar() ?: return@opcode halt()) }

        opcode("noop", 21) { /* no-op */ }
    }

    private fun MutableMap<Int, Opcode>.opcode(
        mnemonic: String,
        code: Int,
        parameters: Int = 0,
        updatePC: Boolean = true,
        handler: Computer.() -> Unit
    ) = put(code, SimpleOpcode(mnemonic, parameters, updatePC, handler))

    private fun MutableMap<Int, Opcode>.jumpOpcode(
        mnemonic: String,
        code: Int,
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
            if (updatePC) pc += parameters + 1
        }
    }

    private class JumpOpcode(
        override val mnemonic: String,
        override val parameters: Int,
        val cond: Computer.() -> Boolean
    ) : Opcode {
        override fun Computer.handle() {
            pc = if (cond()) parameter(parameters - 1)
            else pc + parameters + 1
        }
    }

    sealed interface Opcode {
        val mnemonic: String
        val parameters: Int

        fun Computer.handle()
    }

    operator fun get(op: Int) = supportedOpcodes[op]
}