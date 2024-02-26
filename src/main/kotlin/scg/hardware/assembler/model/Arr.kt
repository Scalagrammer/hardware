package scg.hardware.assembler.model

import scg.hardware.assembler.model.Direction.*
import scg.hardware.getValue
import scg.hardware.setValue

enum class Direction { FORWARD, BACKWARD }

class Arr(private val cells : List<Cell>) : Cell {

    private var direction by ThreadLocal.withInitial{ FORWARD }

    private var iterator by ThreadLocal.withInitial { cells.iterator() }

    override fun invoke(action : (UInt) -> Unit) {
        if (hasNext()) (iterator.next())(action)
    }

    fun hasNext() = iterator.hasNext()

    fun reversed() {

        direction = if (direction == FORWARD) BACKWARD else FORWARD

        iterator = if (direction == FORWARD) {
            cells.iterator()
        } else {
            (cells.reversed()).listIterator()
        }
    }

    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    override fun toString() = when (direction) {
        FORWARD  -> (cells.toString())
        BACKWARD -> (cells.reversed()).toString()
    }

}