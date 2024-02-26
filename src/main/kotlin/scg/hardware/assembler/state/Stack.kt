package scg.hardware.assembler.state

import java.lang.IllegalStateException
import java.lang.StringBuilder

typealias Operand = UInt

interface Stack : AutoCloseable, Iterable<Operand> {
    fun isEmpty() : Boolean
    fun pop() : Operand
    fun peek() : Operand
    fun push(item : Operand)

    fun dup() = push(peek())
    fun dup(times : UInt) { repeat(times.toInt()) { dup() } }
    fun drop(count : UInt) = repeat(count.toInt()) { pop() }
}

class StackImpl : Stack {

    private sealed interface Link {
        val top  : Operand
        val rest : Link
    }

    private data class Cons(
        override val top  : Operand,
        override val rest : Link,
    ) : Link

    private data object Nil : Link {
        override val top : Nothing
            get() { throw IllegalStateException("Stack is empty") }
        override val rest : Nothing
            get() { throw IllegalStateException("Stack is empty") }
    }

    private var top : Link = Nil

    override fun iterator() : Iterator<Operand> =
        fold(mutableListOf<Operand>()) { acc, operand -> acc.apply { add(operand) } }.iterator()

    override fun isEmpty() = top == Nil

    override fun peek() = top.top

    override fun close() { top = Nil }

    override fun pop() : Operand = with(top) { top.also { this@StackImpl.top = rest } }

    override fun push(item : Operand) { top = Cons(item, top) }

    override fun toString() : String {

        tailrec fun Link.toString0(builder : StringBuilder = StringBuilder()) : String =
            when {
                this is Cons && builder.isEmpty() ->
                    rest.toString0(builder.append("[->").append(top))
                this is Cons ->
                    rest.toString0(builder.append(',').append(top))
                else ->
                    builder.append(']').toString()
            }

        return when (top) {
            Nil  -> "[]"
            else -> top.toString0()
        }
    }

    private fun <R> fold(zero : R, folder : (R, Operand) -> R) : R {

        tailrec fun Link.fold(acc : R) : R = when (this) {
            is Cons -> rest.fold(folder(acc, top))
            else -> acc
        }

        return top.fold(zero)

    }

}