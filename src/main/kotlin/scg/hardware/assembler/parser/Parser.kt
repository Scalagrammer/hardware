package scg.hardware.assembler.parser

import scg.hardware.assembler.lexer.*
import scg.hardware.assembler.lexer.TokenType.*
import scg.hardware.assembler.model.Hardware
import scg.hardware.assembler.state.*
import java.text.ParseException

private typealias Section = String

class Parser(private val tokens : MutableList<Token>) {

    // TODO : macro

    private data class Macro(val signature : List<String>, val tokens : List<Token>)

    private var pos = 0

    private var labelsOffset = 0

    private var currentSection : Section? = null

    private val macros = mutableMapOf<String, Macro>()

    fun <H : Hardware<H>> State<H>.parse() {
        while (!match(END_OF_FILE)) when {
            currentIs(INSTRUCTION) -> acceptInstruction()

            match(AT) -> acceptLabel()

            match(DOT) -> acceptSection()

            currentIsMacro() -> applyMacro(nextWord())

            else ->
                throw ParseException("Unexpected token: ${peek()}", pos)
        }
    }

    private fun acceptSection() {

        val section = nextWord()

        when {
            isDefine(section) -> {
                if (isDefine(currentSection))
                    throw ParseException(".define section have to declared at least once", pos)
                if (currentSection != null)
                    throw ParseException(".define section have to declared first of all others", pos)
                currentSection = "define"
            }
            isMacro(section) -> {
                if (currentSection != null && currentSection !in setOf("define", "macro"))
                    throw ParseException(".macro section cannot be declared after '$currentSection' section", pos)
                currentSection = "macro"
                acceptMacro()
            }
            isCode(section) -> {
                if (currentSection != null && currentSection !in setOf("define", "macro"))
                    throw ParseException(".code section cannot be declared after '$currentSection' section", pos)
                currentSection = "code"
            }
        }

        labelsOffset += 1

    }

    private fun <H : Hardware<H>> State<H>.acceptLabel() {

        if (isDefine(currentSection)) {
            throw ParseException("within .define section labels are disabled", pos)
        }

        with(nextId()) {
            this@acceptLabel += value to (position - labelsOffset)
        }.also {
            labelsOffset += 1
        }
    }

    private fun acceptMacro() {

        val name = nextWord()

        val scope = mutableListOf<Token>()

        val signature = mutableListOf<String>()

        while (!currentIs(INSTRUCTION)) {
            signature.add(nextWord())
            skip(COMMA)
        }

        while (!currentIs(DOT)) scope.add(next())

        if (scope.isEmpty()) {
            throw ParseException("missing instructions for .macro '$name'", pos)
        }

        macros[name] = Macro(signature, scope)

    }

    private fun <H : Hardware<H>> State<H>.acceptInstruction() {

        val (name, position) = with(nextId()) { value to position + 1 }

        if (name in setOf("reg", "arr", "usr") && !isDefine(currentSection)) {
            throw ParseException("invalid instruction syntax '$name', allowed only within .define section", pos)
        }

        when (name) {
            "rvr" -> this += Rvr(nextWord())
            "jxt" -> {
                val arrName = nextWord()
                accept(COMMA)
                val label = nextWord()
                this += Jxt(arrName, label)
            }
            "arr" -> {
                val arrName = nextWord()
                accept(L_BRACKET)
                val cells = mutableListOf<Any>()
                while (!match(R_BRACKET)) {
                    when {
                        currentIs(NUMBER) -> cells.add(parseNumber())
                        currentIs(WORD) -> cells.add(nextWord())
                        else -> throw ParseException("Expected number or word", pos)
                    }
                    skip(COMMA)
                }
                this += DefArr(arrName, cells)
            }
            "ret" -> this += if (currentIs(WORD)) RetPsh(nextWord()) else Ret()
            "rst" -> this += Rst(nextWord())
            "clr" -> this += Clr(nextWord())
            "usr" -> {
                val regName = nextWord()
                accept(COMMA)
                val label = nextWord()
                this += Usr(regName, label)
            }
            "ext" -> this += if (currentIs(WORD)) CellExt(nextWord()) else PopExt()
            "pln" -> this += when {
                currentIs(WORD) -> CellPln(nextWord())
                currentIs(NUMBER) -> NumberPln(parseNumber())
                else -> PopPln()
            }
            "jmp" -> this += Jmp(nextWord())
            "jeq" -> this += when {
                currentIs(WORD) -> {
                    val cellName = nextWord()
                    accept(COMMA)
                    CellEqPopJmp(cellName, nextWord())
                }
                else -> {
                    val value = parseNumber()
                    accept(COMMA)
                    NumberEqPopJmp(nextWord(), value)
                }
            }
            "jnq" -> this += when {
                currentIs(WORD) -> {
                    val cellName = nextWord()
                    accept(COMMA)
                    CellNeqPopJmp(cellName, nextWord())
                }
                else -> {
                    val value = parseNumber()
                    accept(COMMA)
                    NumberNeqPopJmp(nextWord(), value)
                }
            }
            "reg" -> {
                val regName = nextWord()
                this += if (match(COMMA)) DefReg(regName, parseNumber()) else DefReg(regName)
            }
            "upd" -> this +=  Upd(nextWord())
            "dup" -> this +=  if (currentIs(WORD)) CellDup(nextWord()) else Dup()
            "mov" -> {
                val to = nextWord()
                this += if (match(COMMA)) when {
                    currentIs(NUMBER) -> NumberMov(to, parseNumber())
                    else -> CellMov(to, nextWord())
                } else PopMov(to)
            }
            "psh" -> this += if (currentIs(NUMBER)) PshNumber(parseNumber()) else CellPsh(nextWord())
            "run" -> this += when {
                pattern(NUMBER, COMMA, WORD) -> {
                    val value = parseNumber()
                    accept(COMMA)
                    NumberTopRun(nextWord(), value)
                }
                pattern(WORD, COMMA, WORD) -> {
                    val cellName = nextWord()
                    accept(COMMA)
                    CellTopRun(nextWord(), cellName)
                }
                else -> Run(nextWord())
            }
            "pop" -> this +=  if (currentIs(WORD)) CellPop(nextWord()) else Pop()
            "cal" -> this += Cal(nextWord())
            "clu" -> this += Clu()
            "seu" -> this += Seu()
            "req" -> {
                val requiredCells = mutableListOf<String>()
                while (!currentIs(INSTRUCTION)) {
                    requiredCells.add(nextWord())
                    skip(COMMA)
                }
                this += Req(requiredCells)
            }
            else  -> when (name) {
                in macros -> applyMacro(name)
                else -> TODO("Instruction $name not implemented yet")
            }
        }
    }

    private fun next() = tokens[pos++]

    private fun nextWord() : String {

        val id = tokens[pos] as? Id ?: throw ParseException("Identifier expected", pos)

        return id.value.also { pos += 1 }

    }

    private fun parseNumber() = with(tokens[pos] as? Id) {

        this ?: throw ParseException("Expected number", pos)

        value.run {
            when {
                startsWith("0x") ->
                    substring(2).toUInt(16)
                startsWith("0b") ->
                    substring(2).toUInt(2)
                else ->
                    toUInt(10)
            }
        }.also { pos += 1 }

    }

    private fun currentIsMacro() : Boolean {

        val token = peek()

        return if (isWord(token)) {
            (token as Id).value in macros
        } else {
            false
        }
    }

    private fun peekNextWord() = (tokens[pos + 1] as Id).value

    private fun peek() : Token = tokens[pos]

    private fun skip(type : TokenType) {
        if (type == peekType()) accept(type)
    }

    private fun nextId() =
        (tokens[pos] as? Id
            ?: throw ParseException("Expected identifier", pos)).also { pos += 1 }

    private fun accept(tokenType : TokenType) =
        if (tokenType == peekType()) {
            pos += 1
        } else {
            throw ParseException("Expected $tokenType", pos)
        }

    private fun peekType() = tokens[pos].type

    private fun <H : Hardware<H>> State<H>.applyMacro(macroName : String) {

        val applyPosition = pos - 1

        val args = mutableListOf<Token>()

        while(!currentIs(INSTRUCTION)) {
            args.add(next())
            skip(COMMA)
        }

        val (params, scope) = macros.getValue(macroName)

        if (params.size != args.size) {
            throw ParseException("Wrong args for .macro '$macroName'", applyPosition)
        }

        val argByParam = params.zip(args).toMap()

        for (i in applyPosition ..< pos) {
            tokens.removeAt(applyPosition)
        }

        pos = applyPosition

        for (token in scope) {
            if (isWord(token) && (token as Id).value in argByParam) {
                tokens.add(pos++, argByParam[token.value]!!)
            } else {
                tokens.add(pos++, token)
            }
        }

        pos = applyPosition

        acceptInstruction()

    }

    private fun currentIsEither(
        firstTokenType : TokenType,
        secondTokenType : TokenType,
    ) = tokens[pos].type == firstTokenType || tokens[pos].type == secondTokenType

    private fun currentIs(tokenType : TokenType) = tokens[pos].type == tokenType

    private fun match(tokenType : TokenType) = (tokenType == peekType()).also { if (it) pos += 1 }

    private fun pattern(
        firstTokenType : TokenType,
        secondTokenType : TokenType,
        thirdTokenType : TokenType,
    ) = tokens[pos].type == firstTokenType && tokens[pos + 1].type == secondTokenType && tokens[pos + 2].type == thirdTokenType

}

private fun isMacro(section : Section?) = section == "macro"
private fun isCode(section : Section?) = section == "code"
private fun isDefine(section : Section?) = section == "define"