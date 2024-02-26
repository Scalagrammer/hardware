package scg.hardware.assembler.lexer

import scg.hardware.assembler.lexer.TokenType.*

sealed interface Token {
    val type : TokenType
    val position : Int
}

data class Term(
    override val position : Int,
    override val type: TokenType,
) : Token

data class Id(
    override val position : Int,
    val value : String,
    override val type: TokenType,
) : Token

fun isWord(token : Token) = token.type == WORD


