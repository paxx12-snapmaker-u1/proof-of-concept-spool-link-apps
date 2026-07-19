package dev.pages.paxx12.spoollink.formats

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.util.Log
import dev.pages.paxx12.spoollink.model.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SnapmakerFormat"

object SnapmakerFormat {
    private val SALT_KEY_A = "Snapmaker_qwertyuiop[,.;]".toByteArray(Charsets.UTF_8)
    private val SALT_KEY_B = "Snapmaker_qwertyuiop[,.;]_1q2w3e".toByteArray(Charsets.UTF_8)

    private val MAIN_TYPE_MAP = mapOf(
        1 to "PLA", 2 to "PETG", 3 to "ABS", 4 to "TPU", 5 to "PVA",
        6 to "ASA", 9 to "PA", 10 to "PA-CF", 11 to "PA-GF", 12 to "PC",
        20 to "PLA-CF", 22 to "PEBA", 23 to "TPE"
    )

    private val SUB_TYPE_MAP = mapOf(
        0 to "", 1 to "Basic", 2 to "Matte", 3 to "SnapSpeed", 4 to "Silk",
        5 to "Support", 6 to "HF", 7 to "95A", 8 to "95A HF",
        9 to "90A", 10 to "85A", 11 to "Wood", 12 to "Translucent",
        13 to "Full Spectrum"
    )

    fun tryRead(tag: Tag): SnapmakerTagPayload? = tryReadWithRaw(tag)?.first

    fun tryReadWithRaw(tag: Tag): Pair<SnapmakerTagPayload, ByteArray>? {
        val mc = MifareClassic.get(tag) ?: return null
        if (mc.size != MifareClassic.SIZE_1K) return null

        val uid = tag.id
        val keysA = deriveKeys(uid, SALT_KEY_A, 'a')
        val keysB = deriveKeys(uid, SALT_KEY_B, 'b')

        return try {
            mc.connect()
            // 1024 bytes = 16 sectors × 4 blocks × 16 bytes
            val data = ByteArray(1024)

            for (sector in 0 until 16) {
                val authed = runCatching {
                    mc.authenticateSectorWithKeyA(sector, keysA[sector])
                }.getOrDefault(false) || runCatching {
                    mc.authenticateSectorWithKeyB(sector, keysB[sector])
                }.getOrDefault(false)

                if (!authed) {
                    mc.close()
                    return null
                }

                val firstBlock = mc.sectorToBlock(sector)
                val blockCount = mc.getBlockCountInSector(sector)
                // skip last block (trailer with keys/access bits)
                for (b in 0 until blockCount - 1) {
                    val blockData = mc.readBlock(firstBlock + b)
                    blockData.copyInto(data, sector * 64 + b * 16)
                }
            }

            mc.close()
            parseData(data, uid)?.let { Pair(it, data) }
        } catch (e: Exception) {
            runCatching { mc.close() }
            null
        }
    }

    // HKDF-SHA256: PRK = HMAC(salt, IKM); key_i = HMAC(PRK, "key_{type}_{i}" || 0x01)[0:6]
    private fun deriveKeys(ikm: ByteArray, salt: ByteArray, keyType: Char): Array<ByteArray> {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        return Array(16) { i ->
            val info = "key_${keyType}_$i".toByteArray(Charsets.UTF_8)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.doFinal(info + byteArrayOf(1)).copyOf(6)
        }
    }

    private fun parseData(data: ByteArray, rawUid: ByteArray): SnapmakerTagPayload? {
        if (data.size != 1024) return null

        fun le16(pos: Int) = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
        fun ascii(pos: Int, len: Int): String {
            val end = (pos until pos + len).firstOrNull { data[it] == 0.toByte() } ?: (pos + len)
            return String(data, pos, end - pos, Charsets.US_ASCII).trim()
        }
        fun rgb(pos: Int) = ((data[pos].toInt() and 0xFF) shl 16) or
                ((data[pos + 1].toInt() and 0xFF) shl 8) or (data[pos + 2].toInt() and 0xFF)

        // Sector 0: UID at 0, vendor at 16, manufacturer at 32
        val vendor = ascii(16, 16)
        val manufacturer = ascii(32, 16)

        // Sector 1, block 0 at byte 64
        val version = le16(64)
        val mainTypeId = le16(66)
        val subTypeId = le16(68)
        val colorNums = data[72].toInt() and 0xFF
        val alpha = 0xFF - (data[73].toInt() and 0xFF)
        // Sector 1, block 1 at byte 80: five RGB triples
        val rgb1 = rgb(80); val rgb2 = rgb(83); val rgb3 = rgb(86)
        val rgb4 = rgb(89); val rgb5 = rgb(92)

        // Sector 2, block 0 at byte 128
        val diameterRaw = le16(128) // units of 0.01 mm (175 = 1.75 mm)
        val weightRaw = le16(130)   // grams
        val lengthRaw = le16(132)   // meters
        // Sector 2, block 1 at byte 144
        val dryTemp = le16(144); val dryTime = le16(146)
        val hotendMax = le16(148);  val hotendMin = le16(150)
        val bedTemp = le16(154)
        val firstLayerTemp = le16(156); val otherLayerTemp = le16(158)
        // Sector 2, block 2 at byte 160
        val mfDate = ascii(160, 8)

        val mainType = MAIN_TYPE_MAP[mainTypeId] ?: return null
        val subType = SUB_TYPE_MAP[subTypeId]?.takeIf { it.isNotEmpty() }
        val uid = rawUid.joinToString("") { "%02X".format(it) }

        return SnapmakerTagPayload(
            uid = uid,
            version = version,
            vendor = vendor.takeIf { it.isNotEmpty() },
            manufacturer = manufacturer.takeIf { it.isNotEmpty() },
            mainType = mainType,
            subType = subType,
            colorNums = colorNums,
            alpha = alpha,
            rgb1 = rgb1, rgb2 = rgb2, rgb3 = rgb3, rgb4 = rgb4, rgb5 = rgb5,
            diameterMm = if (diameterRaw > 0) diameterRaw / 100.0 else null,
            weightG = if (weightRaw > 0) weightRaw else null,
            lengthM = if (lengthRaw > 0) lengthRaw else null,
            dryTempC = if (dryTemp > 0) dryTemp else null,
            dryTimeH = if (dryTime > 0) dryTime else null,
            hotendMaxTempC = if (hotendMax > 0) hotendMax else null,
            hotendMinTempC = if (hotendMin > 0) hotendMin else null,
            bedTempC = if (bedTemp > 0) bedTemp else null,
            firstLayerTempC = if (firstLayerTemp > 0) firstLayerTemp else null,
            otherLayerTempC = if (otherLayerTemp > 0) otherLayerTemp else null,
            mfDate = mfDate.takeIf { it.length == 8 }
        )
    }
}

data class SnapmakerTagPayload(
    val uid: String,
    val version: Int,
    val vendor: String?,
    val manufacturer: String?,
    val mainType: String,
    val subType: String?,
    val colorNums: Int,
    val alpha: Int,
    val rgb1: Int,
    val rgb2: Int,
    val rgb3: Int,
    val rgb4: Int,
    val rgb5: Int,
    val diameterMm: Double?,
    val weightG: Int?,
    val lengthM: Int?,
    val dryTempC: Int?,
    val dryTimeH: Int?,
    val hotendMaxTempC: Int?,
    val hotendMinTempC: Int?,
    val bedTempC: Int?,
    val firstLayerTempC: Int?,
    val otherLayerTempC: Int?,
    val mfDate: String?
) : NFCTagPayload {

    val colorHexString: String get() = "%06X".format(rgb1)

    override val formatName: String get() = "Snapmaker NFC"
    override val typeDescription: String? get() =
        listOfNotNull(mainType, subType).joinToString(" ").takeIf { it.isNotEmpty() }
    override val colorHex: String get() = colorHexString
    override val spoolId: Int? get() = null
    override val displayTitle: String get() =
        listOfNotNull(vendor ?: "Snapmaker", subType ?: mainType).joinToString(" ")

    override val fields: List<TagField>
        get() = buildList {
            add(TagField("Type", mainType))
            subType?.let { add(TagField("Subtype", it)) }
            vendor?.let { add(TagField("Brand", it)) }
            manufacturer?.let { add(TagField("Manufacturer", it)) }
            add(TagField("Color", "#$colorHexString", colorHex = colorHexString))
            if (colorNums > 1) add(TagField("Colors", "$colorNums"))
            diameterMm?.let { add(TagField("Diameter", "${"%.2f".format(it)} mm")) }
            weightG?.let { add(TagField("Weight", "$it g")) }
            if (hotendMinTempC != null && hotendMaxTempC != null)
                add(TagField("Nozzle", "$hotendMinTempC–$hotendMaxTempC °C"))
            else hotendMaxTempC?.let { add(TagField("Nozzle Max", "$it °C")) }
            bedTempC?.let { add(TagField("Bed", "$it °C")) }
            if (dryTempC != null && dryTimeH != null)
                add(TagField("Drying", "$dryTempC °C / $dryTimeH h"))
            mfDate?.let {
                add(TagField("Mfg Date", "${it.take(4)}-${it.drop(4).take(2)}-${it.drop(6)}"))
            }
            add(TagField("UID", uid))
        }

    override val filamentMetadata: FilamentMetadata
        get() = FilamentMetadata(
            brand = vendor,
            material = mainType,
            subtype = subType,
            colorHex = colorHexString,
            diameter = diameterMm,
            weight = weightG?.toDouble(),
            nozzleTemp = hotendMaxTempC,
            bedTemp = bedTempC,
            spoolId = null
        )

    fun toJson(): String = buildString {
        append("{")
        fun kv(k: String, v: Any?) {
            if (v == null) return
            if (length > 1) append(",")
            append("\"$k\":")
            when (v) {
                is String -> append("\"${v.replace("\"", "\\\"")}\"")
                is Double -> append("${"%.4f".format(v)}")
                else -> append(v)
            }
        }
        kv("format", "snapmaker-mifare")
        kv("uid", uid)
        kv("version", version)
        kv("vendor", vendor)
        kv("manufacturer", manufacturer)
        kv("mainType", mainType)
        kv("subType", subType)
        kv("colorHex", colorHexString)
        kv("alpha", alpha)
        kv("colorNums", colorNums)
        if (colorNums > 1) {
            kv("rgb2", "%06X".format(rgb2))
            kv("rgb3", "%06X".format(rgb3))
            kv("rgb4", "%06X".format(rgb4))
            kv("rgb5", "%06X".format(rgb5))
        }
        kv("diameterMm", diameterMm)
        kv("weightG", weightG)
        kv("lengthM", lengthM)
        kv("hotendMinTempC", hotendMinTempC)
        kv("hotendMaxTempC", hotendMaxTempC)
        kv("bedTempC", bedTempC)
        kv("firstLayerTempC", firstLayerTempC)
        kv("otherLayerTempC", otherLayerTempC)
        kv("dryTempC", dryTempC)
        kv("dryTimeH", dryTimeH)
        kv("mfDate", mfDate)
        append("}")
    }
}
