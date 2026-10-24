package org.opendnevnik.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.opendnevnik.shared.schedule.ScheduleJson
import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException

class BoundedUtf8Test {
    @Test fun readsCyrillic() {
        assertEquals("ЗС", readBoundedUtf8(ByteArrayInputStream("ЗС".toByteArray(Charsets.UTF_8))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedFile() {
        readBoundedUtf8(ByteArrayInputStream(ByteArray(ScheduleJson.MAX_BYTES + 1)))
    }
    @Test(expected = CharacterCodingException::class) fun rejectsBrokenUtf8() {
        readBoundedUtf8(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
    }
    @Test fun acceptsExactByteLimit() {
        val text = "a".repeat(ScheduleJson.MAX_BYTES)
        assertEquals(text, readBoundedUtf8(ByteArrayInputStream(text.toByteArray())))
    }
}
