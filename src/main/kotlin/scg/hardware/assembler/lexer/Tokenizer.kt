package scg.hardware.assembler.lexer

import scg.hardware.assembler.lexer.TokenType.*
import java.util.regex.Pattern

object Tokenizer {

    // TODO : replace with Antlr

    private val numberLiteralRegex = "0x[0-9a-fA-F]+|0b[01]+|\\d+".toRegex()

    private val splitLinePattern = Pattern.compile("\\s+")

    operator fun invoke(source : String) = buildList {
        (source.lines()).filterNot { it.startsWith(";") || (it.trim()).isEmpty() }
            .withIndex()
            .forEach { (index, line) ->
                for (token in line.split(splitLinePattern)) {

                    if (token.isBlank()) continue

                    var nextToken = token

                    when {
                        nextToken.startsWith("[") -> {
                            tokenize("[", index)
                            nextToken = nextToken.removePrefix("[")
                        }

                        nextToken.startsWith("@") -> {
                            tokenize("@", index)
                            nextToken = nextToken.removePrefix("@")
                        }

                        nextToken.startsWith(".") -> {
                            tokenize(".", index)
                            nextToken = nextToken.removePrefix(".")
                        }

                        nextToken.startsWith(",") -> {
                            tokenize(",", index)
                            nextToken = nextToken.removePrefix(",")
                        }
                    }

                    if (nextToken.endsWith(",")) {
                        tokenize(nextToken.removeSuffix(","), index)
                        tokenize(",", index)
                    } else if (nextToken.endsWith("]")) {
                        tokenize(nextToken.removeSuffix("]"), index)
                        tokenize("]", index)
                    } else {
                        tokenize(nextToken, index)
                    }
                }
            }

        add(Term(source.length, END_OF_FILE))
    }.toMutableList()

    private fun MutableList<Token>.tokenize(word : String, lineIndex : Int) {
        when {
            isInstruction(word) ->
                add(Id(lineIndex, word, INSTRUCTION))

            isNumber(word) ->
                add(Id(lineIndex, word, NUMBER))

            isComma(word) ->
                add(Term(lineIndex, COMMA))

            isSharp(word) ->
                add(Term(lineIndex, SHARP))

            isLB(word) ->
                add(Term(lineIndex, L_BRACKET))

            isRB(word) ->
                add(Term(lineIndex, R_BRACKET))

            isAt(word) ->
                add(Term(lineIndex, AT))

            isDot(word) ->
                add(Term(lineIndex, DOT))

            isWord(word) ->
                add(Id(lineIndex, word, WORD))
        }
    }

    fun isSharp(word : String) = word == "#"

    fun isWord(word : String) = word.isNotBlank()

    fun isComma(word : String) = word == ","

    fun isLB(word : String) = word == "["

    fun isRB(word : String) = word == "]"

    fun isAt(word : String) = word == "@"

    fun isDot(word : String) = word == "."

    fun isNumber(word : String) = word.matches(numberLiteralRegex)

    fun isInstruction(word : String) = word in instructionSet

    private val instructionSet = buildSet {
        add("rvr")
        add("arr")
        add("clu")
        add("seu")
        add("ret")
        add("rst")
        add("clr")
        add("usr")
        add("upd")
        add("ext")
        add("pln")
        add("jmp")
        add("jeq")
        add("jnq")
        add("jxt")
        add("reg")
        add("dup")
        add("mov")
        add("psh")
        add("bpt")
        add("run")
        add("pop")
        add("cal")
        add("swp")
        add("rvt")
        add("req")
    }
}