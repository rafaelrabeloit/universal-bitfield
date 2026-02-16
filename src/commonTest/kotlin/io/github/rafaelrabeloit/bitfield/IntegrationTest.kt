package io.github.rafaelrabeloit.bitfield

import io.github.rafaelrabeloit.bitfield.dsl.bitfield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Integration tests that define real EMV schemas, parse real-world byte values,
 * and verify the complete end-to-end output including callbacks.
 *
 * These tests validate PRD milestone M2 Task 5: "Define an AIP schema, parse
 * real EMV data, verify output."
 */
class IntegrationTest {

    // ---- Application Interchange Profile (Tag 82, 2 bytes) ----

    private val aipSchema = bitfield("Application Interchange Profile", bytes = 2) {
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

    @Test
    fun aipParseSdaDdaCardWithEmvMode() {
        // 0x60 = 0b01100000: DDA(bit1)=1, SDA(bit2)=1
        // 0x80 = 0b10000000: EMV Mode(bit0)=1
        val result = aipSchema.parse(byteArrayOf(0x60, 0x80.toByte()))

        assertEquals(11, result.entries.size)
        assertEquals(0, result.entries[0].rawBits)  // CDA not supported
        assertEquals(1, result.entries[1].rawBits)  // DDA supported
        assertEquals(1, result.entries[2].rawBits)  // SDA supported
        assertEquals(0, result.entries[3].rawBits)  // CV not supported
        assertEquals(0, result.entries[4].rawBits)  // TRM not performed
        assertEquals(0, result.entries[5].rawBits)  // Issuer Auth not supported
        assertEquals(0, result.entries[6].rawBits)  // On-device CV not supported
        assertEquals("RFU", result.entries[7].resolvedLabel)
        assertEquals(1, result.entries[8].rawBits)  // EMV Mode
        assertEquals(0, result.entries[9].rawBits)  // Contactless EMV not supported
        assertEquals("RFU", result.entries[10].resolvedLabel)
    }

    @Test
    fun aipParseFullFeaturedCard() {
        // 0xFE = 0b11111110: all features set except RFU(bit7)
        // 0xC0 = 0b11000000: EMV + Contactless EMV
        val result = aipSchema.parse(byteArrayOf(0xFE.toByte(), 0xC0.toByte()))

        assertEquals(1, result.entries[0].rawBits)  // CDA
        assertEquals(1, result.entries[1].rawBits)  // DDA
        assertEquals(1, result.entries[2].rawBits)  // SDA
        assertEquals(1, result.entries[3].rawBits)  // CV
        assertEquals(1, result.entries[4].rawBits)  // TRM
        assertEquals(1, result.entries[5].rawBits)  // Issuer Auth
        assertEquals(1, result.entries[6].rawBits)  // On-device CV
        assertEquals(1, result.entries[8].rawBits)  // EMV Mode
        assertEquals(1, result.entries[9].rawBits)  // Contactless EMV
    }

    @Test
    fun aipParseEmptyCard() {
        // No features supported
        val result = aipSchema.parse(byteArrayOf(0x00, 0x00))

        for (entry in result.entries) {
            if (entry.field is RfuField) {
                assertEquals("RFU", entry.resolvedLabel)
            } else {
                assertEquals(0, entry.rawBits, "Expected ${entry.field.name} to be unset")
            }
        }
    }

    @Test
    fun aipRejectWrongByteCount() {
        assertFailsWith<IllegalArgumentException> {
            aipSchema.parse(byteArrayOf(0x00))
        }
        assertFailsWith<IllegalArgumentException> {
            aipSchema.parse(byteArrayOf(0x00, 0x00, 0x00))
        }
    }

    @Test
    fun aipIterateResultsByByte() {
        val result = aipSchema.parse(byteArrayOf(0x19, 0x80.toByte()))

        // Verify we can walk entries grouped by byte
        val byte1Entries = result.entries.filter { it.byteIndex == 1 }
        val byte2Entries = result.entries.filter { it.byteIndex == 2 }

        assertEquals(8, byte1Entries.size)  // 7 bits + 1 rfu
        assertEquals(3, byte2Entries.size)  // 2 bits + 1 rfu range

        // Verify field names for byte 1
        assertEquals("CDA Supported", byte1Entries[0].field.name)
        assertEquals("DDA Supported", byte1Entries[1].field.name)
        assertEquals("SDA Supported", byte1Entries[2].field.name)
        assertEquals("Cardholder Verification is supported", byte1Entries[3].field.name)
        assertEquals("Terminal Risk Management is to be performed", byte1Entries[4].field.name)
        assertEquals("Issuer Authentication is supported", byte1Entries[5].field.name)
        assertEquals("On-device Cardholder Verification is supported", byte1Entries[6].field.name)
        assertEquals("RFU", byte1Entries[7].field.name)
    }

    @Test
    fun aipEntryForLookup() {
        val result = aipSchema.parse(byteArrayOf(0x80.toByte(), 0x00))

        val cdaField = aipSchema.bytes[0].fields[0]
        val cdaEntry = result.entryFor(1, cdaField)
        assertEquals(1, cdaEntry.rawBits)
        assertEquals("CDA Supported", cdaEntry.resolvedLabel)

        val emvField = aipSchema.bytes[1].fields[0]
        val emvEntry = result.entryFor(2, emvField)
        assertEquals(0, emvEntry.rawBits)
    }

    // ---- Cryptogram Information Data (Tag 9F27, 1 byte) ----

    private val cidSchema = bitfield("Cryptogram Information Data", bytes = 1) {
        byte(1, "Byte 1") {
            enum(0..1, "Cryptogram Type (high)") {
                value(0b00, "Application Authentication Cryptogram (AAC)")
                value(0b01, "Authorization Request Cryptogram (ARQC)")
                value(0b10, "Transaction Certificate (TC)")
                value(0b11, "Reserved for Future Use")
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
                value(0b00, "Application Authentication Cryptogram (AAC)")
                value(0b01, "Authorization Request Cryptogram (ARQC)")
                value(0b10, "Transaction Certificate (TC)")
                value(0b11, "Reserved for Future Use")
            }
        }
    }

    @Test
    fun cidParseArqcWithIadAndAtc() {
        // 0b01000110 = 0x46: high=01(ARQC), rfu=00, IAD=0(not provided), ATC=1(provided), low=10(TC)
        val result = cidSchema.parse(byteArrayOf(0x46))

        assertEquals(5, result.entries.size)
        assertEquals("Authorization Request Cryptogram (ARQC)", result.entries[0].resolvedLabel)
        assertEquals("RFU", result.entries[1].resolvedLabel)
        assertEquals("IAD not provided", result.entries[2].resolvedLabel)
        assertEquals("ATC provided", result.entries[3].resolvedLabel)
        assertEquals("Transaction Certificate (TC)", result.entries[4].resolvedLabel)
    }

    @Test
    fun cidParseAac() {
        // 0b00000000 = 0x00: high=00(AAC), rfu=00, IAD=0, ATC=0, low=00(AAC)
        val result = cidSchema.parse(byteArrayOf(0x00))

        assertEquals("Application Authentication Cryptogram (AAC)", result.entries[0].resolvedLabel)
        assertEquals("IAD not provided", result.entries[2].resolvedLabel)
        assertEquals("ATC not provided", result.entries[3].resolvedLabel)
        assertEquals("Application Authentication Cryptogram (AAC)", result.entries[4].resolvedLabel)
    }

    @Test
    fun cidParseTcWithAllIndicators() {
        // 0b10000111 = 0x87: high=10(TC), rfu=00, IAD=0, ATC=1, low=11(RFU)
        // Wait, let me recalculate:
        // 0b10001111 = 0x8F: high=10(TC), rfu=00, IAD=1(provided), ATC=1(provided), low=11(RFU)
        val result = cidSchema.parse(byteArrayOf(0x8F.toByte()))

        assertEquals("Transaction Certificate (TC)", result.entries[0].resolvedLabel)
        assertEquals("IAD provided", result.entries[2].resolvedLabel)
        assertEquals("ATC provided", result.entries[3].resolvedLabel)
        assertEquals("Reserved for Future Use", result.entries[4].resolvedLabel)
    }

    @Test
    fun cidCallbacksFire() {
        val callLog = mutableListOf<String>()

        val cidWithCallbacks = bitfield("CID", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Cryptogram Type") {
                    value(0b00, "AAC") { callLog.add("AAC") }
                    value(0b01, "ARQC") { callLog.add("ARQC") }
                    value(0b10, "TC") { callLog.add("TC") }
                    value(0b11, "RFU") { callLog.add("RFU") }
                }
                rfu(2..3)
                bit(4, "IAD") {
                    set("provided")
                    unset("not provided")
                    onSet { callLog.add("IAD-set") }
                    onUnset { callLog.add("IAD-unset") }
                }
                bit(5, "ATC") {
                    set("provided")
                    unset("not provided")
                    onSet { callLog.add("ATC-set") }
                }
                enum(6..7, "Cryptogram Type (low)") {
                    value(0b00, "AAC")
                    value(0b01, "ARQC") { callLog.add("ARQC-low") }
                    value(0b10, "TC")
                    value(0b11, "RFU")
                }
            }
        }

        // 0b01001001 = 0x49: high=01(ARQC), rfu=00, IAD=1, ATC=0, low=01(ARQC)
        cidWithCallbacks.parse(byteArrayOf(0x49))

        assertEquals(listOf("ARQC", "IAD-set", "ARQC-low"), callLog)
    }

    // ---- Terminal Verification Results (Tag 95, 5 bytes) — simplified ----

    private val tvrSchema = bitfield("Terminal Verification Results", bytes = 5) {
        byte(1, "Offline Data Authentication") {
            bit(0, "Offline data authentication was not performed")
            bit(1, "SDA failed")
            bit(2, "ICC data missing")
            bit(3, "Card appears on terminal exception file")
            bit(4, "DDA failed")
            bit(5, "CDA failed")
            rfu(6..7)
        }
        byte(2, "Terminal and Application") {
            bit(0, "ICC and terminal have different application versions")
            bit(1, "Expired application")
            bit(2, "Application not yet effective")
            bit(3, "Requested service not allowed for card product")
            bit(4, "New card")
            rfu(5..7)
        }
        byte(3, "Cardholder Verification") {
            bit(0, "Cardholder verification was not successful")
            bit(1, "Unrecognised CVM")
            bit(2, "PIN Try Limit exceeded")
            bit(3, "PIN entry required and PIN pad not present or not working")
            bit(4, "PIN entry required, PIN pad present, but PIN was not entered")
            bit(5, "Online PIN entered")
            rfu(6..7)
        }
        byte(4, "Terminal Risk Management") {
            bit(0, "Transaction exceeds floor limit")
            bit(1, "Lower consecutive offline limit exceeded")
            bit(2, "Upper consecutive offline limit exceeded")
            bit(3, "Transaction selected randomly for online processing")
            bit(4, "Merchant forced transaction online")
            rfu(5..7)
        }
        byte(5, "Issuer Script") {
            bit(0, "Default TDOL used")
            bit(1, "Issuer authentication failed")
            bit(2, "Script processing failed before final GENERATE AC")
            bit(3, "Script processing failed after final GENERATE AC")
            rfu(4..7)
        }
    }

    @Test
    fun tvrParseCleanTransaction() {
        // All zeros = no issues found
        val result = tvrSchema.parse(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00))

        assertEquals(31, result.entries.size)
        val nonRfuEntries = result.entries.filter { it.field !is RfuField }
        assertTrue(nonRfuEntries.all { it.rawBits == 0 }, "All flags should be unset for clean transaction")
    }

    @Test
    fun tvrParseSdaFailedAndExpired() {
        // Byte 1: 0x40 = 0b01000000 -> SDA failed (bit 1)
        // Byte 2: 0x40 = 0b01000000 -> Expired application (bit 1)
        // Bytes 3-5: clean
        val result = tvrSchema.parse(byteArrayOf(0x40, 0x40, 0x00, 0x00, 0x00))

        val sdaFailed = result.entries.first { it.field.name == "SDA failed" }
        assertEquals(1, sdaFailed.rawBits)

        val expired = result.entries.first { it.field.name == "Expired application" }
        assertEquals(1, expired.rawBits)

        // Other flags should be unset
        val otherFlags = result.entries.filter {
            it.field !is RfuField && it.field.name != "SDA failed" && it.field.name != "Expired application"
        }
        assertTrue(otherFlags.all { it.rawBits == 0 })
    }

    @Test
    fun tvrParseMultipleIssues() {
        // Byte 1: 0x80 = bit0 set (Offline data auth not performed)
        // Byte 3: 0x84 = 0b10000100 = bit0 + bit5 set (CV not successful + Online PIN entered)
        val result = tvrSchema.parse(byteArrayOf(0x80.toByte(), 0x00, 0x84.toByte(), 0x00, 0x00))

        val odaNotPerformed = result.entries.first { it.field.name == "Offline data authentication was not performed" }
        assertEquals(1, odaNotPerformed.rawBits)

        val cvNotSuccessful = result.entries.first { it.field.name == "Cardholder verification was not successful" }
        assertEquals(1, cvNotSuccessful.rawBits)

        val onlinePin = result.entries.first { it.field.name == "Online PIN entered" }
        assertEquals(1, onlinePin.rawBits)
    }

    @Test
    fun tvrSchemaStructure() {
        assertEquals("Terminal Verification Results", tvrSchema.name)
        assertEquals(5, tvrSchema.expectedBytes)
        assertEquals(5, tvrSchema.bytes.size)

        assertEquals("Offline Data Authentication", tvrSchema.bytes[0].label)
        assertEquals("Terminal and Application", tvrSchema.bytes[1].label)
        assertEquals("Cardholder Verification", tvrSchema.bytes[2].label)
        assertEquals("Terminal Risk Management", tvrSchema.bytes[3].label)
        assertEquals("Issuer Script", tvrSchema.bytes[4].label)
    }

    // ---- Schema reuse: ActionCode template (IAC Default/Denial/Online share TVR layout) ----

    @Test
    fun schemaReuseDifferentDataSameStructure() {
        // The TVR schema can be reused to parse IAC Default, IAC Denial, IAC Online
        // because they share the same bit layout
        val iacDefault = tvrSchema.parse(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00, 0x00))
        val iacDenial = tvrSchema.parse(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x80.toByte()))

        val defaultBit0 = iacDefault.entries.first { it.byteIndex == 1 && it.field.name == "Offline data authentication was not performed" }
        assertEquals(1, defaultBit0.rawBits)

        val denialBit0 = iacDenial.entries.first { it.byteIndex == 5 && it.field.name == "Default TDOL used" }
        assertEquals(1, denialBit0.rawBits)
    }

    // ---- Application Usage Control (Tag 9F07, 2 bytes) ----

    @Test
    fun applicationUsageControlParse() {
        val aucSchema = bitfield("Application Usage Control", bytes = 2) {
            byte(1, "Byte 1") {
                bit(0, "Valid for domestic cash transactions")
                bit(1, "Valid for international cash transactions")
                bit(2, "Valid for domestic goods")
                bit(3, "Valid for international goods")
                bit(4, "Valid for domestic services")
                bit(5, "Valid for international services")
                bit(6, "Valid at ATMs")
                bit(7, "Valid at terminals other than ATMs")
            }
            byte(2, "Byte 2") {
                bit(0, "Domestic cashback allowed")
                bit(1, "International cashback allowed")
                rfu(2..7)
            }
        }

        // 0xFF = all domestic+international+ATM features, 0xC0 = both cashback allowed
        val result = aucSchema.parse(byteArrayOf(0xFF.toByte(), 0xC0.toByte()))

        // All byte 1 flags should be set
        val byte1Entries = result.entries.filter { it.byteIndex == 1 }
        assertTrue(byte1Entries.all { it.rawBits == 1 })

        // Cashback flags set
        assertEquals(1, result.entries.first { it.field.name == "Domestic cashback allowed" }.rawBits)
        assertEquals(1, result.entries.first { it.field.name == "International cashback allowed" }.rawBits)

        // Domestic only card: 0x55 = 0b01010101 (even bits), 0x80 = domestic cashback only
        val domesticResult = aucSchema.parse(byteArrayOf(0xAA.toByte(), 0x80.toByte()))
        assertEquals(1, domesticResult.entries.first { it.field.name == "Valid for domestic cash transactions" }.rawBits)
        assertEquals(0, domesticResult.entries.first { it.field.name == "Valid for international cash transactions" }.rawBits)
        assertEquals(1, domesticResult.entries.first { it.field.name == "Valid for domestic goods" }.rawBits)
        assertEquals(0, domesticResult.entries.first { it.field.name == "Valid for international goods" }.rawBits)
    }

    // ---- End-to-end callback integration ----

    @Test
    fun endToEndCallbackBuildsExplanationModel() {
        // Simulates how emv-tools would use callbacks to build an explanation
        val explanationLines = mutableListOf<String>()

        val schema = bitfield("AIP", bytes = 2) {
            byte(1, "Byte 1") {
                bit(0, "CDA Supported") {
                    onSet { explanationLines.add("CDA: Supported") }
                    onUnset { explanationLines.add("CDA: Not supported") }
                }
                bit(1, "DDA Supported") {
                    onSet { explanationLines.add("DDA: Supported") }
                    onUnset { explanationLines.add("DDA: Not supported") }
                }
                bit(2, "SDA Supported") {
                    onSet { explanationLines.add("SDA: Supported") }
                    onUnset { explanationLines.add("SDA: Not supported") }
                }
                rfu(3..7)
            }
            byte(2, "Byte 2") {
                bit(0, "EMV Mode") {
                    onSet { explanationLines.add("EMV: Supported") }
                    onUnset { explanationLines.add("EMV: Not supported") }
                }
                rfu(1..7)
            }
        }

        // 0x60 = DDA + SDA set, 0x80 = EMV Mode set
        schema.parse(byteArrayOf(0x60, 0x80.toByte()))

        assertEquals(
            listOf(
                "CDA: Not supported",
                "DDA: Supported",
                "SDA: Supported",
                "EMV: Supported"
            ),
            explanationLines
        )
    }
}
