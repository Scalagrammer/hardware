package scg.hardware.assembler.lexer

enum class TokenType {
    INSTRUCTION,
    WORD,
    NUMBER,
    COMMA,
    L_BRACKET,
    R_BRACKET,
    AT,
    DOT,
    SHARP,
    END_OF_FILE
}