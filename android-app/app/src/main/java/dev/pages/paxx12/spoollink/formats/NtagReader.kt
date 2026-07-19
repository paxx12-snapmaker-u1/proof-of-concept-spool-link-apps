package dev.pages.paxx12.spoollink.formats

import android.nfc.Tag
import android.nfc.tech.MifareUltralight

object NtagReader {
    private const val MAX_PAGES = 256 // safety cap; NTAG216 tops out at 231 pages

    fun tryReadRaw(tag: Tag): ByteArray? {
        val mu = MifareUltralight.get(tag) ?: return null
        return try {
            mu.connect()
            val result = mutableListOf<Byte>()
            var page = 0
            while (page < MAX_PAGES) {
                val chunk = try { mu.readPages(page) } catch (_: Exception) { null } ?: break
                result.addAll(chunk.toList())
                page += 4
            }
            mu.close()
            if (result.isEmpty()) null else result.toByteArray()
        } catch (_: Exception) {
            runCatching { mu.close() }
            null
        }
    }
}
