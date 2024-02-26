package scg.hardware.assembler

import scg.hardware.applyOn
import scg.hardware.assembler.lexer.Tokenizer
import scg.hardware.assembler.model.Hardware
import scg.hardware.assembler.parser.Parser
import scg.hardware.assembler.state.Interpreter

interface Program<H : Hardware<H>> {

    operator fun invoke(hardware : H)

    companion object {
        infix fun <H : Hardware<H>> H.runWith(source : () -> String) {
            Tokenizer(source()).applyOn(Interpreter<H>()) { Parser(it).run { parse() } }.invoke(this)
        }
    }
}