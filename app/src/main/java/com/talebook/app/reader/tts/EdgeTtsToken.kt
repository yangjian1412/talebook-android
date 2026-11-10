package com.talebook.app.reader.tts

import java.nio.charset.StandardCharsets

/**
 * Edge 在线语音用的 Sec-MS-GEC 令牌（v2 格式）。
 *
 * 微软把这个校验藏在服务里：令牌由「当前时间（5 分钟取整）+ 固定的客户端标识」算出来，
 * 每 5 分钟内有效。算法是改过初值的 SHA-256，再在外面套一层哈希，照着官方客户端实现搬过来。
 */
object EdgeTtsToken {

    /** Edge 客户端里写死的标识。 */
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"

    /** 现在用的版本号，跟 Edge 的 Chromium 版本走。 */
    const val SEC_MS_GEC_VERSION = "2-143.0.3650.75"

    private const val MASK = 0xFFFFFFFFL
    private const val UNIX_TO_WINDOWS_EPOCH = 11644473600L

    private val SHA256_INITIAL = longArrayOf(
        0x6A09E667L, 0xBB67AE85L, 0x3C6EF372L, 0xA54FF53AL,
        0x510E527FL, 0x9B05688CL, 0x1F83D9ABL, 0x5BE0CD19L
    )
    private val SHA256_STATE = longArrayOf(
        0xFD9FBEA2L, 0x91243CE7L, 0xBE499094L, 0xCD2E1E0AL,
        0x6C006F49L, 0x04BD35ABL, 0xFEB0A7E4L, 0x03EFFBD2L
    )
    private val SHA256_SCHEDULE = longArrayOf(
        0xEB24BF9EL, 0x1558691FL, 0xA0C09EE2L, 0x0CFA32BAL,
        0xA622EE8CL, 0xD1DD9491L, 0xC0DD25A0L, 0xF9E70AB6L,
        0xF0C31573L, 0x7058CE4EL, 0x7188E606L, 0x00E5AB33L,
        0xD258B060L, 0x72766564L
    )
    private val ROUND_CONSTANTS = longArrayOf(
        0x428A2F98L, 0x71374491L, 0xB5C0FBCFL, 0xE9B5DBA5L, 0x3956C25BL, 0x59F111F1L, 0x923F82A4L, 0xAB1C5ED5L,
        0xD807AA98L, 0x12835B01L, 0x243185BEL, 0x550C7DC3L, 0x72BE5D74L, 0x80DEB1FEL, 0x9BDC06A7L, 0xC19BF174L,
        0xE49B69C1L, 0xEFBE4786L, 0x0FC19DC6L, 0x240CA1CCL, 0x2DE92C6FL, 0x4A7484AAL, 0x5CB0A9DCL, 0x76F988DAL,
        0x983E5152L, 0xA831C66DL, 0xB00327C8L, 0xBF597FC7L, 0xC6E00BF3L, 0xD5A79147L, 0x06CA6351L, 0x14292967L,
        0x27B70A85L, 0x2E1B2138L, 0x4D2C6DFCL, 0x53380D13L, 0x650A7354L, 0x766A0ABBL, 0x81C2C92EL, 0x92722C85L,
        0xA2BFE8A1L, 0xA81A664BL, 0xC24B8B70L, 0xC76C51A3L, 0xD192E819L, 0xD6990624L, 0xF40E3585L, 0x106AA070L,
        0x19A4C116L, 0x1E376C08L, 0x2748774CL, 0x34B0BCB5L, 0x391C0CB3L, 0x4ED8AA4AL, 0x5B9CCA4FL, 0x682E6FF3L,
        0x748F82EEL, 0x78A5636FL, 0x84C87814L, 0x8CC70208L, 0x90BEFFFAL, 0xA4506CEBL, 0xBEF9A3F7L, 0xC67178F2L
    )
    private const val SHA256_ADJUSTMENT = 0xC9DC06A7L
    private const val SIGMA0_ADJUSTMENT = 0x059C0B80L
    private const val SIGMA1_ADJUSTMENT = 0x000B92C0L

    private val CS64_COEFFICIENTS = arrayOf(
        longArrayOf(0xE82E91B1L, 0xB32700D0L), longArrayOf(0x44DE24C6L, 0x88DFF51DL), longArrayOf(0x3BEB65BCL, 0x29514038L),
        longArrayOf(0xC89C5933L, 0xB328CBDFL), longArrayOf(0x8D1EC211L, 0xD405B4D5L), longArrayOf(0x314EF5D5L, 0x26E55F3CL),
        longArrayOf(0xE92491B1L, 0x607801C6L), longArrayOf(0xF0A5B9D5L, 0x82B2C1FBL), longArrayOf(0x06064CF3L, 0xFA51A3DAL),
        longArrayOf(0xDA79FA60L, 0x207CC464L), longArrayOf(0xBED20560L, 0x3C3C74F3L), longArrayOf(0x89F9C9F8L, 0xD2D14B61L),
        longArrayOf(0x35FA38C6L, 0xBB04A2C1L), longArrayOf(0xCB49E3C6L, 0xC5626167L)
    )
    private const val CS64_FACTOR = 0x82893933L
    private const val CS64_ADJUSTMENT0 = 0xF2F90B1AL
    private const val CS64_ADJUSTMENT1 = 0x0B1A0000L

    /** 按当前时间生成令牌；同一个 5 分钟里可以重复用。 */
    fun generate(unixSeconds: Long = System.currentTimeMillis() / 1000L): String {
        var ticks = unixSeconds + UNIX_TO_WINDOWS_EPOCH
        ticks -= ticks % 300
        ticks *= 10_000_000L
        val input = "$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(StandardCharsets.US_ASCII)
        val digest = sha256Digest(input)
        val cs64 = cs64Digest(input, digest)
        return le32Hex(cs64[0]) + le32Hex(cs64[1]) + "-" + hex(digest)
    }

    private fun rol16(value: Long): Long {
        val value32 = value and MASK
        return ((value32 shl 16) or (value32 shr 16)) and MASK
    }

    private fun rotr(value: Long, bits: Int): Long {
        val value32 = value and MASK
        return ((value32 shr bits) or (value32 shl (32 - bits))) and MASK
    }

    private fun sigma0(value: Long): Long = (rotr(value, 7) xor rotr(value, 18) xor (value shr 3)) and MASK

    private fun sigma1(value: Long): Long = (rotr(value, 17) xor rotr(value, 19) xor (value shr 10)) and MASK

    private fun wordsLittleEndian(data: ByteArray): MutableList<Long> {
        val paddedLength = data.size + ((4 - data.size % 4) % 4)
        val words = ArrayList<Long>(paddedLength / 4 + 1)
        var index = 0
        while (index < paddedLength) {
            var word = 0L
            for (byte in 0..3) {
                val position = index + byte
                if (position < data.size) word = word or ((data[position].toLong() and 0xFFL) shl (8 * byte))
            }
            words.add(word)
            index += 4
        }
        if (words.isEmpty()) return words
        if (words.size % 2 != 0) words.add(0L)
        return words
    }

    private fun sha256Rounds(state: LongArray, words: List<Long>, firstRound: Int): LongArray {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        val constants = if (firstRound == 14) blockConstants() else ROUND_CONSTANTS
        for (index in firstRound until 64) {
            val sum1 = (rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)) and MASK
            val choose = (g xor (e and (f xor g))) and MASK
            val sum0 = (rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)) and MASK
            val majority = ((a and b) or (c and (a or b))) and MASK
            val t1 = (h + sum1 + choose + constants[index] + words[index]) and MASK
            val t2 = (sum0 + majority) and MASK
            h = g
            g = f
            f = e
            e = (d + t1) and MASK
            d = c
            c = b
            b = a
            a = (t1 + t2) and MASK
        }
        return longArrayOf(a, b, c, d, e, f, g, h)
    }

    /** 把第 15 个轮常量换掉的那一份。 */
    private fun blockConstants(): LongArray = ROUND_CONSTANTS.copyOf().also { it[14] = SHA256_ADJUSTMENT }

    private fun expandSchedule(words: MutableList<Long>, first: Int) {
        for (index in first until 64) {
            val one = sigma1(words[index - 2])
            val two = sigma0(words[index - 15])
            words.add((one + words[index - 7] + two + words[index - 16]) and MASK)
        }
    }

    private fun sha256Init(data: ByteArray): LongArray {
        val words = ArrayList<Long>(64)
        for (index in 0 until 14) words.add(0L)
        words.add(
            ((data[0].toLong() and 0xFFL) shl 16) or ((data[1].toLong() and 0xFFL) shl 8) or (data[2].toLong() and 0xFFL)
        )
        words.add(
            ((data[3].toLong() and 0xFFL) shl 24) or ((data[4].toLong() and 0xFFL) shl 16) or
                ((data[5].toLong() and 0xFFL) shl 8) or (data[6].toLong() and 0xFFL)
        )
        for (index in 16 until 30) {
            var value = SHA256_SCHEDULE[index - 16]
            if (index - 2 >= 14) {
                val part = sigma1(words[index - 2])
                value += if (index == 16) part xor SIGMA1_ADJUSTMENT else part
            }
            if (index - 7 >= 14) value += words[index - 7]
            if (index - 15 >= 14) {
                val part = sigma0(words[index - 15])
                value += if (index == 29) part xor SIGMA0_ADJUSTMENT else part
            }
            words.add(value and MASK)
        }
        words.add(
            (
                sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] +
                    SHA256_ADJUSTMENT - ROUND_CONSTANTS[14]
                ) and MASK
        )
        expandSchedule(words, 31)
        val working = sha256Rounds(SHA256_STATE, words, 14)
        return LongArray(8) { index -> (SHA256_INITIAL[index] + working[index]) and MASK }
    }

    private fun sha256Digest(data: ByteArray): ByteArray {
        require(data.size >= 7) { "令牌内容至少要 7 个字节" }
        val length = 57L + data.size
        val paddingLength = Math.floorMod(55L - length, 64L).toInt()
        val padded = ByteArray((data.size - 7) + 1 + paddingLength + 8)
        System.arraycopy(data, 7, padded, 0, data.size - 7)
        padded[data.size - 7] = 0x80.toByte()
        val bitLength = length * 8
        for (index in 0 until 8) {
            padded[padded.size - 8 + index] = (bitLength shr (56 - 8 * index)).toByte()
        }
        var state = sha256Init(data.copyOfRange(0, 7))
        var offset = 0
        while (offset < padded.size) {
            val words = ArrayList<Long>(64)
            for (index in 0 until 16) {
                val position = offset + index * 4
                words.add(
                    ((padded[position].toLong() and 0xFFL) shl 24) or ((padded[position + 1].toLong() and 0xFFL) shl 16) or
                        ((padded[position + 2].toLong() and 0xFFL) shl 8) or (padded[position + 3].toLong() and 0xFFL)
                )
            }
            expandSchedule(words, 16)
            val working = sha256Rounds(state, words, 0)
            state = LongArray(8) { index -> (state[index] + working[index]) and MASK }
            offset += 64
        }
        val out = ByteArray(32)
        for (index in 0 until 8) {
            out[index * 4] = (state[index] shr 24).toByte()
            out[index * 4 + 1] = (state[index] shr 16).toByte()
            out[index * 4 + 2] = (state[index] shr 8).toByte()
            out[index * 4 + 3] = state[index].toByte()
        }
        return out
    }

    private fun readLittleEndian32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFFL) or ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or ((data[offset + 3].toLong() and 0xFFL) shl 24)

    private fun cs64Digest(data: ByteArray, digest: ByteArray): LongArray {
        val keyA = readLittleEndian32(digest, 0) or 1L
        val keyB = readLittleEndian32(digest, 4) or 1L
        val factors = longArrayOf((keyA * CS64_FACTOR) and MASK, (keyB * CS64_FACTOR) and MASK)
        val terms = ArrayList<Long>(CS64_COEFFICIENTS.size + 16)
        val offsets = ArrayList<Long>(CS64_COEFFICIENTS.size + 16)
        CS64_COEFFICIENTS.forEachIndexed { index, coefficient ->
            terms.add((coefficient[0] * factors[index % 2]) and MASK)
            offsets.add(coefficient[1])
        }
        val withZero = ByteArray(data.size + 1)
        System.arraycopy(data, 0, withZero, 1, data.size)
        wordsLittleEndian(withZero).forEachIndexed { index, word ->
            var term = word * if (index % 2 == 0) keyA else keyB
            var offset = rol16(word) * if (index % 2 == 0) 0xEF0569FBL else 0xC31713DBL
            if (index == 0) {
                term += CS64_ADJUSTMENT0 * factors[0]
                offset += CS64_ADJUSTMENT1
            }
            terms.add(term and MASK)
            offsets.add(offset and MASK)
        }
        var swapAcc = 0L
        var swapLast = 0L
        var state = 0L
        var acc = 0L
        var index = 0
        while (index < terms.size) {
            val x = terms[index]
            val y = terms[index + 1]
            var mixed = rol16((offsets[index] + x) and MASK)
            var product = (mixed * 0x76F50F34L) and MASK
            swapAcc += (rol16(product) * 0xC3EFEA97L + product) and MASK
            mixed = rol16((offsets[index + 1] + y) and MASK)
            product = (mixed * 0x1881CE3CL) and MASK
            swapLast = (rol16(product) * 0x35BD1EC9L + product) and MASK
            swapAcc = (swapAcc + swapLast) and MASK

            state = (state * keyA + x) and MASK
            state = (rol16(state) * 0xCF98B111L) and MASK
            state = (rol16(state) * 0x87085B9FL) and MASK
            state = (rol16(state) * 0x12CEB96DL) and MASK
            state = (rol16(state) * 0x257E1D83L) and MASK
            acc += state
            var other = (state * keyB + y) and MASK
            other = (rol16(other) * 0xA27416F5L) and MASK
            other = (rol16(other) * 0xD38396FFL) and MASK
            state = (rol16((rol16(other) * 0x7C932B89L) and MASK) * 0xBFA49F69L) and MASK
            acc = (acc + state) and MASK
            index += 2
        }
        return longArrayOf((swapLast xor state) and MASK, (swapAcc xor acc) and MASK)
    }

    private fun le32Hex(value: Long): String = buildString {
        for (shift in 0..3) append(String.format(java.util.Locale.US, "%02X", (value shr (8 * shift)) and 0xFFL))
    }

    private fun hex(bytes: ByteArray): String = buildString {
        bytes.forEach { byte -> append(String.format(java.util.Locale.US, "%02X", byte.toLong() and 0xFFL)) }
    }
}
