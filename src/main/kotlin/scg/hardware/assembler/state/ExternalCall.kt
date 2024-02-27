package scg.hardware.assembler.state

import scg.hardware.assembler.model.Hardware

abstract class ExternalCall<H : Hardware<H>>(val label : Label) : Instruction<H>