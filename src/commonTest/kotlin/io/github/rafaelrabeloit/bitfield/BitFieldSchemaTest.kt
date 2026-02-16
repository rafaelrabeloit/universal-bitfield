package io.github.rafaelrabeloit.bitfield

import io.github.rafaelrabeloit.bitfield.dsl.bitfield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BitFieldSchemaTest {

    // --- EMV-realistic integration tests (PRD Section 9) ---

    @Test
    fun aipSchemaParseRealEmvData() {
        val aipSchema = bitfield("Application Interchange Profile", bytes = 2) {
            byte(1, "Byte 1") {
                bit(0, "CDA Supported")
                bit(1, "DDA Supported")
                bit(2, "SDA Supported")
                bit(3, "Cardholder Verification is supported")
                bit(4, "Terminal Risk Management is to be performed")
                bit(5, "Issuer Authentication is supported")
                bit(6, "On-device Cardholder Verification is supported")
                rfu(7)
            }
            byte(2, "Byte 2") {
                bit(0, "EMV Mode supported")
                bit(1, "Contactless EMV Mode supported")
                rfu(2..7)
            }
        }

        // 0x19 = 0b00011001: bits 3,4,7 set (from MSB: 0=0,1=0,2=0,3=1,4=1,5=0,6=0,7=1)
        // 0x80 = 0b10000000: bit 0 set
        val result = aipSchema.parse(byteArrayOf(0x19, 0x80.toByte()))

        assertEquals("Application Interchange Profile", result.schema.name)
        assertEquals(11, result.entries.size)

        // Byte 1: check which flags are set/unset
        assertEquals(0, result.entries[0].rawBits) // CDA not supported
        assertEquals(0, result.entries[1].rawBits) // DDA not supported
        assertEquals(0, result.entries[2].rawBits) // SDA not supported
        assertEquals(1, result.entries[3].rawBits) // CV supported
        assertEquals(1, result.entries[4].rawBits) // TRM to be performed
        assertEquals(0, result.entries[5].rawBits) // Issuer Auth not supported
        assertEquals(0, result.entries[6].rawBits) // On-device CV not supported

        // Byte 2: EMV Mode set, Contactless EMV not set
        assertEquals(1, result.entries[8].rawBits)
        assertEquals(0, result.entries[9].rawBits)
    }

    @Test
    fun cidSchemaWithMixedEnumsAndBits() {
        val cidSchema = bitfield("Cryptogram Information Data", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Cryptogram Type (high)") {
                    value(0b00, "AAC")
                    value(0b01, "ARQC")
                    value(0b10, "TC")
                    value(0b11, "RFU")
                }
                rfu(2..3)
                bit(4, "Issuer Application Data") {
                    set("IAD provided")
                    unset("IAD not provided")
                }
                bit(5, "Application Transaction Counter") {
                    set("ATC provided")
                    unset("ATC not provided")
                }
                enum(6..7, "Cryptogram Type (low)") {
                    value(0b00, "AAC")
                    value(0b01, "ARQC")
                    value(0b10, "TC")
                    value(0b11, "RFU")
                }
            }
        }

        // 0b10001110 = 0x8E: high=10(TC), rfu=00, IAD=1(set), ATC=1(set), low=10(TC)
        val result = cidSchema.parse(byteArrayOf(0x8E.toByte()))

        assertEquals("TC", result.entries[0].resolvedLabel)          // high enum
        assertEquals("RFU", result.entries[1].resolvedLabel)          // rfu
        assertEquals("IAD provided", result.entries[2].resolvedLabel) // bit 4
        assertEquals("ATC provided", result.entries[3].resolvedLabel) // bit 5
        assertEquals("TC", result.entries[4].resolvedLabel)           // low enum
    }

    // --- Edge cases: parsing ---

    @Test
    fun parseAllZeroBytes() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag A") {
                    set("A set")
                    unset("A unset")
                }
                bit(1, "Flag B") {
                    set("B set")
                    unset("B unset")
                }
                rfu(2..7)
            }
        }

        val result = schema.parse(byteArrayOf(0x00))
        assertEquals("A unset", result.entries[0].resolvedLabel)
        assertEquals("B unset", result.entries[1].resolvedLabel)
    }

    @Test
    fun parseAllOnesBytes() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag A") {
                    set("A set")
                    unset("A unset")
                }
                bit(1, "Flag B") {
                    set("B set")
                    unset("B unset")
                }
                rfu(2..7)
            }
        }

        val result = schema.parse(byteArrayOf(0xFF.toByte()))
        assertEquals("A set", result.entries[0].resolvedLabel)
        assertEquals("B set", result.entries[1].resolvedLabel)
    }

    @Test
    fun parseSingleBitWithNoUnsetMeaning() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Feature X")
                rfu(1..7)
            }
        }

        // When bit is unset and no unsetMeaning is provided, should fall back to setMeaning
        val result = schema.parse(byteArrayOf(0x00))
        assertEquals("Feature X", result.entries[0].resolvedLabel)
        assertEquals(0, result.entries[0].rawBits)
    }

    @Test
    fun parseEnumWithUnmappedValue() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Type") {
                    value(0b00, "Known")
                    // 0b01, 0b10, 0b11 not mapped
                }
                rfu(2..7)
            }
        }

        // 0b01000000 = 0x40 -> bits 0..1 = 01, not mapped
        val result = schema.parse(byteArrayOf(0x40))
        assertEquals("Unknown (1)", result.entries[0].resolvedLabel)
    }

    @Test
    fun parseFiveByteSchema() {
        val schema = bitfield("TVR-like", bytes = 5) {
            for (i in 1..5) {
                byte(i, "Byte $i") {
                    for (b in 0..7) {
                        bit(b, "Byte$i-Bit$b")
                    }
                }
            }
        }

        val data = byteArrayOf(0xFF.toByte(), 0x00, 0xAA.toByte(), 0x55, 0x0F)
        val result = schema.parse(data)

        assertEquals(40, result.entries.size)

        // Byte 1 (0xFF): all bits set
        for (i in 0..7) {
            assertEquals(1, result.entries[i].rawBits, "Byte 1, bit $i should be set")
        }

        // Byte 2 (0x00): all bits unset
        for (i in 8..15) {
            assertEquals(0, result.entries[i].rawBits, "Byte 2, bit ${i - 8} should be unset")
        }

        // Byte 3 (0xAA = 0b10101010): alternating pattern
        assertEquals(1, result.entries[16].rawBits) // bit 0
        assertEquals(0, result.entries[17].rawBits) // bit 1
        assertEquals(1, result.entries[18].rawBits) // bit 2
        assertEquals(0, result.entries[19].rawBits) // bit 3
    }

    @Test
    fun parseAllRfuByte() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                rfu(0..7)
            }
        }

        val result = schema.parse(byteArrayOf(0xAB.toByte()))
        assertEquals(1, result.entries.size)
        assertEquals("RFU", result.entries[0].resolvedLabel)
    }

    @Test
    fun parseThreeBitEnum() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..2, "Wide Enum") {
                    value(0b000, "Val0")
                    value(0b001, "Val1")
                    value(0b010, "Val2")
                    value(0b011, "Val3")
                    value(0b100, "Val4")
                    value(0b101, "Val5")
                    value(0b110, "Val6")
                    value(0b111, "Val7")
                }
                rfu(3..7)
            }
        }

        // 0b10100000 = 0xA0 -> bits 0..2 = 101 = Val5
        val result = schema.parse(byteArrayOf(0xA0.toByte()))
        assertEquals("Val5", result.entries[0].resolvedLabel)
        assertEquals(0b101, result.entries[0].rawBits)
    }

    // --- Callback edge cases ---

    @Test
    fun callbacksNotFiredForNullCallbacks() {
        // Schema with no callbacks - just ensure no exceptions
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag")
                rfu(1..7)
            }
        }

        // Should not throw
        schema.parse(byteArrayOf(0xFF.toByte()))
        schema.parse(byteArrayOf(0x00))
    }

    @Test
    fun multipleCallbacksAcrossBytes() {
        val callLog = mutableListOf<String>()

        val schema = bitfield("Test", bytes = 2) {
            byte(1, "Byte 1") {
                bit(0, "A") {
                    onSet { callLog.add("A-set") }
                    onUnset { callLog.add("A-unset") }
                }
                rfu(1..7)
            }
            byte(2, "Byte 2") {
                bit(0, "B") {
                    onSet { callLog.add("B-set") }
                    onUnset { callLog.add("B-unset") }
                }
                rfu(1..7)
            }
        }

        // Byte 1: bit 0 set, Byte 2: bit 0 unset
        schema.parse(byteArrayOf(0x80.toByte(), 0x00))
        assertEquals(listOf("A-set", "B-unset"), callLog)
    }

    @Test
    fun enumCallbackOnlyForMatchedValue() {
        val callLog = mutableListOf<String>()

        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Type") {
                    value(0b00, "Zero") { callLog.add("Zero") }
                    value(0b01, "One") { callLog.add("One") }
                    value(0b10, "Two") { callLog.add("Two") }
                    value(0b11, "Three") { callLog.add("Three") }
                }
                rfu(2..7)
            }
        }

        // Should only call "One"
        schema.parse(byteArrayOf(0b01000000))
        assertEquals(listOf("One"), callLog)
    }

    // --- entryFor edge cases ---

    @Test
    fun entryForWithMultipleBytes() {
        val schema = bitfield("Test", bytes = 2) {
            byte(1, "Byte 1") {
                bit(0, "A")
                rfu(1..7)
            }
            byte(2, "Byte 2") {
                bit(0, "B")
                rfu(1..7)
            }
        }

        val result = schema.parse(byteArrayOf(0x80.toByte(), 0x00))

        val fieldB = schema.bytes[1].fields[0]
        val entry = result.entryFor(2, fieldB)
        assertEquals(0, entry.rawBits)
        assertEquals("B", entry.resolvedLabel)
    }

    @Test
    fun entryForThrowsWhenNotFound() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "A")
                rfu(1..7)
            }
        }

        val otherSchema = bitfield("Other", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "B")
                rfu(1..7)
            }
        }

        val result = schema.parse(byteArrayOf(0x00))
        val foreignField = otherSchema.bytes[0].fields[0]

        assertFailsWith<NoSuchElementException> {
            result.entryFor(1, foreignField)
        }
    }

    // --- FieldDefinition property tests ---

    @Test
    fun singleBitFieldStartEndBit() {
        val field = SingleBitFlag(bit = 3, name = "Test", setMeaning = "Set", unsetMeaning = null)
        assertEquals(3, field.startBit)
        assertEquals(3, field.endBit)
        assertEquals(3..3, field.bits)
    }

    @Test
    fun multiBitEnumStartEndBit() {
        val field = MultiBitEnum(bits = 2..5, name = "Test", values = emptyMap())
        assertEquals(2, field.startBit)
        assertEquals(5, field.endBit)
    }

    @Test
    fun rfuFieldName() {
        val field = RfuField(bits = 0..7)
        assertEquals("RFU", field.name)
        assertEquals(0, field.startBit)
        assertEquals(7, field.endBit)
    }

    // --- Schema structural tests ---

    @Test
    fun schemaWithAllFieldTypes() {
        val schema = bitfield("Mixed", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag") {
                    set("On")
                    unset("Off")
                }
                enum(1..2, "Mode") {
                    value(0b00, "A")
                    value(0b01, "B")
                    value(0b10, "C")
                    value(0b11, "D")
                }
                rfu(3)
                rfu(4..7)
            }
        }

        val fields = schema.bytes[0].fields
        assertEquals(4, fields.size)
        assertIs<SingleBitFlag>(fields[0])
        assertIs<MultiBitEnum>(fields[1])
        assertIs<RfuField>(fields[2])
        assertIs<RfuField>(fields[3])
    }

    @Test
    fun parseSchemaWithAllFieldTypes() {
        val schema = bitfield("Mixed", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag") {
                    set("On")
                    unset("Off")
                }
                enum(1..2, "Mode") {
                    value(0b00, "A")
                    value(0b01, "B")
                    value(0b10, "C")
                    value(0b11, "D")
                }
                rfu(3)
                rfu(4..7)
            }
        }

        // 0b11100000 = 0xE0: bit0=1(On), bits1..2=11(D), bit3=0, bits4..7=0000
        val result = schema.parse(byteArrayOf(0xE0.toByte()))
        assertEquals("On", result.entries[0].resolvedLabel)
        assertEquals("D", result.entries[1].resolvedLabel)
        assertEquals("RFU", result.entries[2].resolvedLabel)
        assertEquals("RFU", result.entries[3].resolvedLabel)
    }
}
