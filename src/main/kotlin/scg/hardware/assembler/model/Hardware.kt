package scg.hardware.assembler.model

import scg.hardware.assembler.state.State

interface Hardware<H : Hardware<H>> {
    fun State<H>.wire()
}