package scg.hardware.assembler.state

import scg.hardware.assembler.model.Hardware

interface ExternalCall<H : Hardware<H>> : Instruction<H> {
    val label : Label
}