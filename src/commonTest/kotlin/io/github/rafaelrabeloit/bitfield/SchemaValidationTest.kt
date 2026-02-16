package io.github.rafaelrabeloit.bitfield

import io.github.rafaelrabeloit.bitfield.dsl.bitfield
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SchemaValidationTest {

    @Test
    fun byteCountMismatchTooFew() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 2) {
                byte(1, "Byte 1") {
                    bit(0, "A"); bit(1, "B"); bit(2, "C"); bit(3, "D")
                    bit(4, "E"); bit(5, "F"); bit(6, "G"); bit(7, "H")
                }
            }
        }.also {
            assert(it.message!!.contains("declares 2 byte(s) but 1"))
        }
    }

    @Test
    fun byteCountMismatchTooMany() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 1) {
                byte(1, "Byte 1") {
                    bit(0, "A"); bit(1, "B"); bit(2, "C"); bit(3, "D")
                    bit(4, "E"); bit(5, "F"); bit(6, "G"); bit(7, "H")
                }
                byte(2, "Byte 2") {
                    bit(0, "A"); bit(1, "B"); bit(2, "C"); bit(3, "D")
                    bit(4, "E"); bit(5, "F"); bit(6, "G"); bit(7, "H")
                }
            }
        }.also {
            assert(it.message!!.contains("declares 1 byte(s) but 2"))
        }
    }

    @Test
    fun byteIndicesNotSequential() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 2) {
                byte(1, "Byte 1") {
                    bit(0, "A"); bit(1, "B"); bit(2, "C"); bit(3, "D")
                    bit(4, "E"); bit(5, "F"); bit(6, "G"); bit(7, "H")
                }
                byte(3, "Byte 3") {
                    bit(0, "A"); bit(1, "B"); bit(2, "C"); bit(3, "D")
                    bit(4, "E"); bit(5, "F"); bit(6, "G"); bit(7, "H")
                }
            }
        }.also {
            assert(it.message!!.contains("has index 3, expected 2"))
        }
    }

    @Test
    fun overlappingBitsDetected() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 1) {
                byte(1, "Byte 1") {
                    bit(0, "Flag A")
                    bit(0, "Flag B")
                    rfu(1..7)
                }
            }
        }.also {
            assert(it.message!!.contains("bit 0 is covered by multiple fields"))
        }
    }

    @Test
    fun overlappingEnumAndBit() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 1) {
                byte(1, "Byte 1") {
                    enum(0..1, "Type") {
                        value(0b00, "A")
                        value(0b01, "B")
                    }
                    bit(1, "Overlap")
                    rfu(2..7)
                }
            }
        }.also {
            assert(it.message!!.contains("bit 1 is covered by multiple fields"))
        }
    }

    @Test
    fun incompleteBitCoverage() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 1) {
                byte(1, "Byte 1") {
                    bit(0, "Flag A")
                    bit(1, "Flag B")
                    // bits 2..7 not covered
                }
            }
        }.also {
            assert(it.message!!.contains("not covered by any field"))
        }
    }

    @Test
    fun enumValueOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 1) {
                byte(1, "Byte 1") {
                    enum(0..1, "Type") {
                        value(0b00, "A")
                        value(0b01, "B")
                        value(0b100, "Out of range")
                    }
                    rfu(2..7)
                }
            }
        }.also {
            assert(it.message!!.contains("out of range for 2-bit field"))
        }
    }

    @Test
    fun bitOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            bitfield("Test", bytes = 1) {
                byte(1, "Byte 1") {
                    bit(8, "Out of range")
                    rfu(0..7)
                }
            }
        }.also {
            assert(it.message!!.contains("outside valid range 0..7"))
        }
    }

    @Test
    fun validSchemaPassesAllChecks() {
        val schema = bitfield("Valid", bytes = 2) {
            byte(1, "Byte 1") {
                enum(0..1, "Type") {
                    value(0b00, "A")
                    value(0b01, "B")
                    value(0b10, "C")
                    value(0b11, "D")
                }
                bit(2, "Flag")
                rfu(3..7)
            }
            byte(2, "Byte 2") {
                bit(0, "X"); bit(1, "Y"); bit(2, "Z"); bit(3, "W")
                bit(4, "V"); bit(5, "U"); bit(6, "T"); bit(7, "S")
            }
        }

        assert(schema.expectedBytes == 2)
        assert(schema.bytes.size == 2)
    }
}
