package scg.hardware.assembler.state

import scg.hardware.assembler.Program
import scg.hardware.assembler.model.Cell
import scg.hardware.assembler.model.Reg
import scg.hardware.assembler.model.Arr
import scg.hardware.assembler.model.Hardware
import scg.hardware.assembler.model.Reg.Companion.clu
import scg.hardware.assembler.state.Instruction.Companion.frameOf
import scg.hardware.getValue
import scg.hardware.setValue
import java.lang.ThreadLocal.withInitial
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean

typealias Action = () -> Unit
typealias Label = String

interface State<H : Hardware<H>> {

    fun defineReg(name : String, initial : UInt? = null) : Reg
    fun defineArr(name : String, cells : () -> List<Cell>): Arr

    fun fireAndForget(action : Action)

    fun lookupReg(name : String) : Reg
    fun extractCell(name : String, action : (UInt) -> Unit)
    fun lookupCell(name : String) : Cell
    fun lookupArr(name : String) : Arr
    fun lookupLabel(name : String) : Int

    fun push(value : UInt)

    fun dropAll()

    fun dup(times : UInt)
    fun drop(times : UInt)

    fun executeNext(hardware : H) : Boolean
    fun externalFrame(index : Int) : List<Instruction<H>>

    fun pop() : UInt

    fun peekOrNull() : UInt?

    fun clu()
    fun seu()

    operator fun plusAssign(call : ExternalCall<H>)
    operator fun plusAssign(instruction : Instruction<H>)
    operator fun plusAssign(indexedLabel : Pair<Label, Int>)

    fun usr(regName : String, action : (UInt?) -> Unit)

    var pointer : Int

}

class Interpreter<H : Hardware<H>> : State<H>, Program<H> {

    private val started = AtomicBoolean(false)

    private var externalCallIndex = -1

    private val regs = HashMap<String, Reg>()

    private val lbls = HashMap<String, Int>()

    private val arrs = HashMap<String, Arr>()


    private val stacks = withInitial(::StackImpl)

    private val stack : Stack by stacks

    private val pointers = withInitial { 0 }

    override var pointer : Int by pointers


    private val instructions  = ArrayList<Instruction<H>>()

    private val externalCalls = HashMap<Int, Instruction<H>>()


    override fun invoke(hardware : H) =
        synchronized(started) {

            if (started.get()) throw IllegalStateException("interpreter is already running")

            doBind(hardware)

            started.set(true)

            while (executeNext(hardware)) Unit
        }

    override fun usr(regName : String, action : (UInt?) -> Unit) =
        lookupReg(regName).usr { value ->
            withFreeResources.use { action(value) }
        }

    override fun defineReg(name : String, initial : UInt?) : Reg {

        if (started.get()) {
            throw IllegalStateException("can not define reg '$name' after the interpreter has been started")
        }

        if (regs.containsKey(name)) {
            throw IllegalStateException("reg $name is already defined")
        }

        return Reg(initial).also { regs[name] = it }

    }

    override fun defineArr(name : String, cells : () -> List<Cell>) : Arr {

        if (started.get()) {
            throw IllegalStateException("can not define arr '$name' after the interpreter has benn started")
        }

        if (arrs.containsKey(name)) {
            throw IllegalStateException("arr $name is already defined")
        }

        return Arr(cells()).also { arrs[name] = it }

    }

    override fun fireAndForget(action : Action) {
        executor.execute {
            withFreeResources.use { action() }
        }
    }

    override fun extractCell(name : String, action : (UInt) -> Unit) =
        (regs[name] ?: arrs[name])?.invoke(action) ?: Unit

    override fun lookupCell(name : String) =
        regs[name] ?: arrs[name] ?: throw IllegalStateException("$name is not defined")

    override fun lookupReg(name : String) =
        regs[name] ?: throw IllegalStateException("$name is not defined")

    override fun lookupArr(name : String) =
        arrs[name] ?: throw IllegalStateException("$name is not defined")

    override fun lookupLabel(name : String) =
        lbls[name] ?: throw IllegalStateException("$name is not defined")

    override fun push(value : UInt) = stack.push(value)

    override fun pop() = stack.pop()

    override fun peekOrNull() = if (stack.isEmpty()) null else stack.peek()

    override fun dup(times : UInt) = stack.dup(times)

    override fun dropAll() = stack.close()

    override fun drop(times : UInt) = stack.drop(times)

    override fun executeNext(hardware : H) : Boolean {

        if (!started.get()) throw IllegalStateException("interpreter is not started")

        if (pointer >= instructions.size) return false

        try {
            instructions[pointer].apply { execute(hardware) }
            pointer += 1
        } catch (flag : JumpFlag) {
            pointer = flag.goto
        } catch (_ : RetFlag) {
            return false
        }

        return true
    }

    override fun externalFrame(index : Int) =
        externalCalls[index]?.let { frameOf(it) }
            ?: throw IllegalArgumentException("external call by index=$index is not defined")

    override fun clu() {

        if (!started.get()) throw IllegalStateException("can not clean updates flag before interpreter has been started")

        Reg.clu()
    }

    override fun seu() {

        if (!started.get()) throw IllegalStateException("can not set updates flag before interpreter has been started")

        Reg.seu()
    }

    private fun doBind(hardware : H) {

        hardware.run { wire() }

        for (i in instructions) i.run { bind(hardware) }

        for (c in externalCalls.values) c.run { bind(hardware) }

    }

    override fun plusAssign(instruction : Instruction<H>) {

        if (started.get()) throw IllegalStateException("can not define instruction after the interpreter has been started")

        instructions += instruction

    }

    override fun plusAssign(indexedLabel : Pair<Label, Int>) {

        val (label, index) = indexedLabel

        if (started.get()) throw IllegalStateException("can not define label=$label after the interpreter has been started")

        if (label in lbls) throw IllegalStateException("label $label is already defined")

        lbls[label] = index

    }

    override fun plusAssign(call : ExternalCall<H>) {

        if (started.get()) throw IllegalStateException("can not define external call for label='${call.label}' after the interpreter has been started")

        externalCallIndex -= 1

        lbls += call.label to externalCallIndex

        externalCalls[externalCallIndex] = call

    }

    private val withFreeResources : AutoCloseable
        get() = AutoCloseable {
            stacks.remove()
            pointers.remove()
        }

    companion object {
        private val executor = ForkJoinPool.commonPool()
    }
}