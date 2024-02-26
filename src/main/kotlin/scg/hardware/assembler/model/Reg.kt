package scg.hardware.assembler.model

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import scg.hardware.traverse
import java.util.concurrent.atomic.AtomicBoolean

typealias Callback = suspend (UInt?) -> Unit

class Reg(initial : UInt? = null) : Iterable<UInt>, Cell {

    private val monitor = object

    @Volatile private var value : UInt? = initial

    private val subscription = ArrayList<Callback>()

    override fun iterator() = synchronized(monitor) {
        when {
            value != null -> listOf(value!!)
            else          -> emptyList()
        }.iterator()
    }

    fun clr() = synchronized(monitor) { value = null }

    fun rst() : Unit = synchronized(monitor) { clr() ; upd() }

    fun usr(callback: Callback) = synchronized(monitor) {

        subscription.add(callback)

        if (value != null) upd()

    }

    override fun invoke(action: (UInt) -> Unit) = synchronized(monitor) { value?.run(action) } ?: Unit

    operator fun invoke(value : UInt) : Unit = synchronized(monitor) {
        this.value = value
        upd()
    }

    fun upd() : Unit = synchronized(monitor) {

        if (!globalUpdatesEnabled.get()) return@synchronized

        if (subscription.isEmpty()) return@synchronized

        usrScope.traverse(subscription) { async { it(value) } }

    }


    override fun toString() = value?.let { "[$it]" } ?: "[_]"

    companion object {

        private val usrScope = CoroutineScope(IO)

        private val globalUpdatesEnabled = AtomicBoolean(false)

        fun seu() { globalUpdatesEnabled.compareAndSet(false, true) }

        fun clu() { globalUpdatesEnabled.compareAndSet(true, false) }

        fun extract(ar : Reg, br : Reg, action : (UInt, UInt) -> Unit) {
            for (a in ar)
            for (b in br) action(a, b)
        }

        fun collect(ar : Reg, br : Reg, cr : Reg, dr : Reg, action : (List<UInt>) -> Unit) {
            for (a in ar)
            for (b in br)
            for (c in cr)
            for (d in dr) action(listOf(a, b, c, d))
        }

    }
}