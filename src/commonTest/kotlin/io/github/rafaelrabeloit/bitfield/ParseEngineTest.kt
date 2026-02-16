package io.github.rafaelrabeloit.bitfield

import io.github.rafaelrabeloit.bitfield.dsl.bitfield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParseEngineTest {

    @Test
    fun parseSingleBitFlags() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag A")
                bit(1, "Flag B")
                rfu(2..7)
            }
        }

        // 0b11000000 = 0xC0 -> bit 0 set, bit 1 set
        val result = schema.parse(byteArrayOf(0xC0.toByte()))

        assertEquals(3, result.entries.size)
        assertEquals("Flag A", result.entries[0].resolvedLabel)
        assertEquals(1, result.entries[0].rawBits)
        assertEquals("Flag B", result.entries[1].resolvedLabel)
        assertEquals(1, result.entries[1].rawBits)
    }

    @Test
    fun parseSingleBitUnset() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Feature") {
                    set("Enabled")
                    unset("Disabled")
                }
                rfu(1..7)
            }
        }

        // 0x00 -> bit 0 is unset
        val result = schema.parse(byteArrayOf(0x00))

        assertEquals("Disabled", result.entries[0].resolvedLabel)
        assertEquals(0, result.entries[0].rawBits)
    }

    @Test
    fun parseSingleBitSet() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Feature") {
                    set("Enabled")
                    unset("Disabled")
                }
                rfu(1..7)
            }
        }

        // 0x80 = 0b10000000 -> bit 0 (MSB) is set
        val result = schema.parse(byteArrayOf(0x80.toByte()))

        assertEquals("Enabled", result.entries[0].resolvedLabel)
        assertEquals(1, result.entries[0].rawBits)
    }

    @Test
    fun parseMultiBitEnum() {
        val schema = bitfield("CID", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Cryptogram Type") {
                    value(0b00, "AAC")
                    value(0b01, "ARQC")
                    value(0b10, "TC")
                    value(0b11, "RFU")
                }
                rfu(2..7)
            }
        }

        // 0b01000000 = 0x40 -> bits 0..1 = 01 = ARQC
        val result = schema.parse(byteArrayOf(0x40))

        assertEquals("ARQC", result.entries[0].resolvedLabel)
        assertEquals(0b01, result.entries[0].rawBits)
    }

    @Test
    fun parseMultiBitEnumAllValues() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Type") {
                    value(0b00, "Zero")
                    value(0b01, "One")
                    value(0b10, "Two")
                    value(0b11, "Three")
                }
                rfu(2..7)
            }
        }

        // Test all 4 values: bits 0..1 are the top 2 bits
        assertEquals("Zero", schema.parse(byteArrayOf(0b00000000.toByte())).entries[0].resolvedLabel)
        assertEquals("One", schema.parse(byteArrayOf(0b01000000.toByte())).entries[0].resolvedLabel)
        assertEquals("Two", schema.parse(byteArrayOf(0b10000000.toByte())).entries[0].resolvedLabel)
        assertEquals("Three", schema.parse(byteArrayOf(0b11000000.toByte())).entries[0].resolvedLabel)
    }

    @Test
    fun parseRfuField() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag")
                rfu(1..7)
            }
        }

        val result = schema.parse(byteArrayOf(0xFF.toByte()))

        assertEquals("RFU", result.entries[1].resolvedLabel)
    }

    @Test
    fun parseMultiByteSchema() {
        val schema = bitfield("AIP", bytes = 2) {
            byte(1, "Byte 1") {
                bit(0, "CDA Supported")
                bit(1, "DDA Supported")
                bit(2, "SDA Supported")
                bit(3, "CV Supported")
                bit(4, "TRM Performed")
                bit(5, "Issuer Auth")
                bit(6, "On-device CV")
                rfu(7)
            }
            byte(2, "Byte 2") {
                bit(0, "EMV Mode")
                bit(1, "Contactless EMV")
                rfu(2..7)
            }
        }

        // 0x19 = 0b00011001, 0x80 = 0b10000000
        val result = schema.parse(byteArrayOf(0x19, 0x80.toByte()))

        // Byte 1: bits 0,1,2 = 0,0,0; bit 3 = 1; bit 4 = 1; bit 5 = 0; bit 6 = 0; bit 7 = 1
        assertEquals(11, result.entries.size) // 8 fields in byte 1 + 3 in byte 2

        // Byte 1 field checks (0x19 = 0b00011001)
        assertEquals(0, result.entries[0].rawBits) // bit 0 (MSB) = 0
        assertEquals(0, result.entries[1].rawBits) // bit 1 = 0
        assertEquals(0, result.entries[2].rawBits) // bit 2 = 0
        assertEquals(1, result.entries[3].rawBits) // bit 3 = 1
        assertEquals(1, result.entries[4].rawBits) // bit 4 = 1
        assertEquals(0, result.entries[5].rawBits) // bit 5 = 0
        assertEquals(0, result.entries[6].rawBits) // bit 6 = 0
        assertEquals(1, result.entries[7].rawBits) // bit 7 (LSB) = 1

        // Byte 2 field checks (0x80 = 0b10000000)
        assertEquals(1, result.entries[8].rawBits)  // bit 0 (MSB) = 1
        assertEquals(0, result.entries[9].rawBits)  // bit 1 = 0
        assertEquals(2, result.entries[8].byteIndex) // byte index is 2
    }

    @Test
    fun parseCallbacksSingleBit() {
        var setCalled = false
        var unsetCalled = false

        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Feature") {
                    onSet { setCalled = true }
                    onUnset { unsetCalled = true }
                }
                rfu(1..7)
            }
        }

        // bit 0 is set (MSB = 1)
        schema.parse(byteArrayOf(0x80.toByte()))
        assertEquals(true, setCalled)
        assertEquals(false, unsetCalled)

        // Reset and test unset
        setCalled = false
        schema.parse(byteArrayOf(0x00))
        assertEquals(false, setCalled)
        assertEquals(true, unsetCalled)
    }

    @Test
    fun parseCallbacksEnum() {
        var matchedValue = -1

        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Type") {
                    value(0b00, "Zero") { matchedValue = 0 }
                    value(0b01, "One") { matchedValue = 1 }
                    value(0b10, "Two") { matchedValue = 2 }
                    value(0b11, "Three") { matchedValue = 3 }
                }
                rfu(2..7)
            }
        }

        // 0b10000000 -> bits 0..1 = 10 = Two
        schema.parse(byteArrayOf(0x80.toByte()))
        assertEquals(2, matchedValue)

        // 0b11000000 -> bits 0..1 = 11 = Three
        schema.parse(byteArrayOf(0xC0.toByte()))
        assertEquals(3, matchedValue)
    }

    @Test
    fun parseInvalidByteCount() {
        val schema = bitfield("Test", bytes = 2) {
            byte(1, "Byte 1") {
                rfu(0..7)
            }
            byte(2, "Byte 2") {
                rfu(0..7)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            schema.parse(byteArrayOf(0x00))
        }

        assertFailsWith<IllegalArgumentException> {
            schema.parse(byteArrayOf(0x00, 0x00, 0x00))
        }
    }

    @Test
    fun entryForLookup() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag A")
                bit(1, "Flag B")
                rfu(2..7)
            }
        }

        val result = schema.parse(byteArrayOf(0x80.toByte()))
        val flagA = schema.bytes[0].fields[0]
        val entry = result.entryFor(1, flagA)

        assertEquals(1, entry.rawBits)
        assertEquals("Flag A", entry.resolvedLabel)
    }

    @Test
    fun parseEnumAtLowBits() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                rfu(0..5)
                enum(6..7, "Low Type") {
                    value(0b00, "L-Zero")
                    value(0b01, "L-One")
                    value(0b10, "L-Two")
                    value(0b11, "L-Three")
                }
            }
        }

        // 0b00000010 = 0x02 -> bits 6..7 = 10 = L-Two
        val result = schema.parse(byteArrayOf(0x02))
        assertEquals("L-Two", result.entries[1].resolvedLabel)
        assertEquals(0b10, result.entries[1].rawBits)

        // 0b00000011 = 0x03 -> bits 6..7 = 11 = L-Three
        val result2 = schema.parse(byteArrayOf(0x03))
        assertEquals("L-Three", result2.entries[1].resolvedLabel)
    }

    @Test
    fun parseResultContainsSchemaAndRawBytes() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                rfu(0..7)
            }
        }

        val data = byteArrayOf(0x42)
        val result = schema.parse(data)

        assertEquals(schema, result.schema)
        assertEquals(data, result.rawBytes)
    }
}
