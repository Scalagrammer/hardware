package scg.hardware

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import scg.hardware.assembler.Program.Companion.runWith
import scg.hardware.assembler.model.Hardware
import scg.hardware.assembler.model.Reg
import scg.hardware.assembler.state.State

class HardwareTest {

    @Test
    fun testArrIteration() {

        val forwardReg  = "rf"
        val backwardReg = "rb"

        val rs = mutableListOf<UInt?>()
        val sr = mutableListOf<UInt?>()

        class SampleHardware : Hardware<SampleHardware> {

            override fun State<SampleHardware>.wire() {
                defineReg(forwardReg).usr(rs::add)
                defineReg(backwardReg).usr(sr::add)
            }

        }

        (SampleHardware()).runWith {
            """.define 
               req $forwardReg, $backwardReg
               arr rs [0x1, 0x2, 0x3, 0x4, 0x5]
               .code
               seu
               @l1
               mov $forwardReg, rs
               jxt rs, l1
               rvr rs
               @l2
               mov $backwardReg, rs
               jxt rs, l2
            """.trimIndent()
        }

        assertArrayEquals(arrayOf(1u, 2u, 3u, 4u, 5u), rs.toTypedArray())
        assertArrayEquals(arrayOf(5u, 4u, 3u, 2u, 1u), sr.toTypedArray())

    }
}