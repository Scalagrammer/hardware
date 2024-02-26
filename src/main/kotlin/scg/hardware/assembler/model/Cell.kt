package scg.hardware.assembler.model

interface Cell {
    operator fun invoke(action : (UInt) -> Unit)
}

data class CellImpl(private val value : UInt) : Cell {

    override fun invoke(action : (UInt) -> Unit) = action(value)

    override fun toString() = value.toString()

}