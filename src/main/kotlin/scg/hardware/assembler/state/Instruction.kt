package scg.hardware.assembler.state

import scg.hardware.assembler.model.*
import kotlin.Int.Companion.MIN_VALUE
import kotlin.properties.Delegates.notNull
import kotlin.system.exitProcess

interface Instruction<H : Hardware<H>> {

    fun State<H>.bind() = Unit
    fun State<H>.execute() = Unit

    fun State<H>.bind(hardware : H) = bind()
    fun State<H>.execute(hardware : H)  = execute()

    companion object {
        @JvmStatic
        fun <H : Hardware<H>> frameOf(instruction : Instruction<H>) = mutableListOf(instruction)
    }

}

class Cal<H : Hardware<H>>(private val label : String) : Instruction<H> {

    private lateinit var instruction : Instruction<H>

    override fun State<H>.bind() {

        val offset = lookupLabel(label)

        instruction = when (offset) {
            in (MIN_VALUE ..< 0) -> ExtCall(externalFrame(offset))
            else                 -> IntCall(offset)
        }.apply { bind() }
    }

    override fun State<H>.execute(hardware : H) = with(instruction) { execute(hardware) }

}

class IntCall<H : Hardware<H>>(private val offset : Int) : Instruction<H> {
    override fun State<H>.execute(hardware : H) {

        val callPointer = pointer

        pointer = offset

        while (executeNext(hardware)) Unit

        pointer = callPointer

    }
}

class ExtCall<H : Hardware<H>>(private val frame : List<Instruction<H>>) : Instruction<H> {
    override fun State<H>.execute(hardware : H) {
        for (i in frame) i.run { execute(hardware) }
    }
}

class Upd<H : Hardware<H>>(private val regName : String) : Instruction<H> {

    private lateinit var reg : Reg

    override fun State<H>.bind() { reg = lookupReg(regName) }

    override fun State<H>.execute() = reg.upd()

}

class Rvr<H : Hardware<H>>(private val itrName : String) : Instruction<H> {

    private lateinit var itr : Arr

    override fun State<H>.bind() { itr = lookupArr(itrName) }

    override fun State<H>.execute() = itr.reversed()

}

class DefArr<H : Hardware<H>>(private val name : String, private val items : List<Any>) : Instruction<H> {
    override fun State<H>.bind() {
        defineArr(name) {
            items.map {
                when (it) {
                    is UInt   -> CellImpl(it)
                    is String -> lookupCell(it)
                    else ->
                        throw IllegalArgumentException("unsupported item type ${it::class.qualifiedName}")
                }
            }
        }
    }
}

class RetPsh<H : Hardware<H>>(private val cellName : String) : Instruction<H> {

    private lateinit var cell : Cell

    override fun State<H>.bind() { cell = lookupCell(cellName) }

    override fun State<H>.execute() = cell(::push)

}

class Clr<H : Hardware<H>>(private val regName : String) : Instruction<H> {

    private lateinit var reg : Reg

    override fun State<H>.bind() { reg = lookupReg(regName) }

    override fun State<H>.execute() = reg.clr()

}

class Rst<H : Hardware<H>>(private val regName : String) : Instruction<H> {

    private lateinit var reg : Reg

    override fun State<H>.bind() { reg = lookupReg(regName) }

    override fun State<H>.execute() = reg.rst()

}

class Usr<H : Hardware<H>>(private val regName : String, private val label : String) : Instruction<H> {

    override fun State<H>.bind(hardware : H) {
        val reg = lookupReg(regName)
        val cal = Cal<H>(label).apply { bind() }
        reg.usr {
            it?.let(::push)
            cal.apply { execute(hardware) }
        }
    }
}

class Pop<H : Hardware<H>>(private val times : UInt = 1U) : Instruction<H> {
    override fun State<H>.execute() = drop(times)
}

class CellPop<H : Hardware<H>>(private val cellName : String) : Instruction<H> {

    private lateinit var cell : Cell

    override fun State<H>.bind() { cell = lookupCell(cellName) }

    override fun State<H>.execute() = cell { drop(it) }

}

class PshNumber<H : Hardware<H>>(private val number : UInt) : Instruction<H> {
    override fun State<H>.execute() = push(number)
}

class CellPsh<H : Hardware<H>>(private val cellName : String) : Instruction<H> {

    private lateinit var cell : Cell

    override fun State<H>.bind() { cell = lookupCell(cellName) }

    override fun State<H>.execute() = cell(::push)

}

class PopMov<H : Hardware<H>>(private val regName : String) : Instruction<H> {

    private lateinit var reg : Reg

    override fun State<H>.bind() { reg = lookupReg(regName) }

    override fun State<H>.execute() = reg(pop())

}

class CellMov<H : Hardware<H>>(private val regName : String, private val cellName : String) : Instruction<H> {

    private lateinit var reg  : Reg
    private lateinit var cell : Cell

    override fun State<H>.bind() {
        reg  = lookupReg(regName)
        cell = lookupCell(cellName)
    }

    override fun State<H>.execute() = cell(reg::invoke)

}

class NumberMov<H : Hardware<H>>(private val regName : String, private val value : UInt) : Instruction<H> {

    private lateinit var reg: Reg

    override fun State<H>.bind() { reg = lookupReg(regName) }

    override fun State<H>.execute() = reg(value)

}

class CellDup<H : Hardware<H>>(private val cellName : String) : Instruction<H> {

    private lateinit var cell : Cell

    override fun State<H>.bind() { cell = lookupCell(cellName) }

    override fun State<H>.execute() = cell(::dup)

}

class Dup<H : Hardware<H>> : Instruction<H> {
    override fun State<H>.execute() = dup(1U)
}

class Run<H : Hardware<H>>(private val label : String) : Instruction<H> {

    private lateinit var cal : Cal<H>

    override fun State<H>.bind() { cal = Cal<H>(label).apply { bind() } }

    override fun State<H>.execute() {
        val top = peekOrNull()
        fireAndForget {
            top?.run(::push)
            cal.apply { execute() }
        }
    }
}

class CellTopRun<H : Hardware<H>>(private val label : String, private val cellName : String) : Instruction<H> {

    private lateinit var cal  : Cal<H>
    private lateinit var cell : Cell

    override fun State<H>.bind() {
        cell = lookupCell(cellName)
        cal  = Cal<H>(label).apply { bind() }
    }

    override fun State<H>.execute() =
        fireAndForget {
            cell(::push)
            cal.apply { execute() }
        }
}

class NumberTopRun<H : Hardware<H>>(private val label : String, private val value : UInt) : Instruction<H> {

    private lateinit var cal : Cal<H>

    override fun State<H>.bind() { cal = Cal<H>(label).apply { bind() } }

    override fun State<H>.execute() =
        fireAndForget {
            push(value)
            cal.apply { execute() }
        }
}

class PopExt<H : Hardware<H>> : Instruction<H> {
    override fun State<H>.execute() = exitProcess((pop()).toInt())
}

class CellExt<H : Hardware<H>>(private val cellName : String) : Instruction<H> {

    private lateinit var cell : Cell

    override fun State<H>.bind() { cell = lookupCell(cellName) }

    override fun State<H>.execute() = cell { exitProcess(it.toInt()) }

}

class PopPln<H : Hardware<H>> : Instruction<H> {
    override fun State<H>.execute() = println(pop())
}

class CellPln<H : Hardware<H>>(private val cellName : String) : Instruction<H> {

    private lateinit var cell : Cell

    override fun State<H>.bind() { cell = lookupCell(cellName) }

    override fun State<H>.execute() = cell(::println)

}

class NumberPln<H : Hardware<H>>(private val value : UInt) : Instruction<H> {
    override fun State<H>.execute() = println(value)
}

class DefReg<H : Hardware<H>>(private val name : String, private val value : UInt? = null) : Instruction<H> {
    override fun State<H>.bind() { defineReg(name, value) }
}

class Jxt<H : Hardware<H>>(private val itrName : String, private val label : String) : Instruction<H> {

    private lateinit var arr : Arr

    private var goto by notNull<Int>()

    override fun State<H>.bind() {
        arr  = lookupArr(itrName)
        goto = lookupLabel(label)
    }

    override fun State<H>.execute() {
        if (arr.hasNext()) throw JumpFlag(goto)
    }
}

class Jmp<H : Hardware<H>>(private val label : String) : Instruction<H> {

    private var goto by notNull<Int>()

    override fun State<H>.bind() { goto = lookupLabel(label) }

    override fun State<H>.execute() = throw JumpFlag(goto)

}

class CellEqPopJmp<H : Hardware<H>>(private val cellName : String, private val label : String) : Instruction<H> {

    private lateinit var cell : Cell

    private var goto by notNull<Int>()

    override fun State<H>.bind() {
        goto = lookupLabel(label)
        cell = lookupCell(cellName)
    }

    override fun State<H>.execute() = cell { if (it == pop()) throw JumpFlag(goto) }

}

class NumberEqPopJmp<H : Hardware<H>>(private val label : String, private val value : UInt) : Instruction<H> {

    private var goto by notNull<Int>()

    override fun State<H>.bind() { goto = lookupLabel(label) }

    override fun State<H>.execute() { if (value == pop()) throw JumpFlag(goto) }

}

class CellNeqPopJmp<H : Hardware<H>>(private val cellName : String, private val label : String) : Instruction<H> {

    private lateinit var cell : Cell

    private var goto by notNull<Int>()

    override fun State<H>.bind() {
        cell = lookupCell(cellName)
        goto = lookupLabel(label)
    }

    override fun State<H>.execute() = cell { if (it != pop()) throw JumpFlag(goto) }

}

class NumberNeqPopJmp<H : Hardware<H>>(private val label : String, private val value : UInt) : Instruction<H> {

    private var goto by notNull<Int>()

    override fun State<H>.bind() { goto = lookupLabel(label) }

    override fun State<H>.execute() { if (value != pop()) throw JumpFlag(goto) }

}

class Req<H : Hardware<H>>(private val requiredCells : List<String>) : Instruction<H> {
    override fun State<H>.bind() = requiredCells.forEach(::lookupCell)
}

class Ret<H : Hardware<H>> : Instruction<H> {
    override fun State<H>.execute() = throw RetFlag
}

class Clu<H : Hardware<H>> : Instruction<H> {
    override fun State<H>.execute() = clu()
}

class Seu<H : Hardware<H>> : Instruction<H> {
    override fun State<H>.execute() = seu()
}

class JumpFlag(val goto : Int) : Throwable() {
    override fun fillInStackTrace() = this
}

object RetFlag : Throwable() {
    override fun fillInStackTrace() = this
}