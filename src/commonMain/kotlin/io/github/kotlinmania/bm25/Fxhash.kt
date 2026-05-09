// port-lint: ignore
// Local port of the upstream `fxhash` 0.2.1 crate (https://crates.io/crates/fxhash) used by
// `bm25` for token hashing. This is a temporary in-tree port; once a `fxhash-kotlin` sibling
// repo exists it should be extracted there. The output matches upstream byte-for-byte for
// UTF-8 string input via the standard library's `Hash for str` impl, which writes the bytes
// followed by a 0xff terminator (Rust 1.71+).
package io.github.kotlinmania.bm25

internal object Fxhash {
    private const val ROTATE: Int = 5
    private const val SEED64: ULong = 0x517c_c1b7_2722_0a95uL
    private const val SEED32: UInt = 0x2722_0a95u
    private const val STR_TERMINATOR: UInt = 0xFFu

    fun hash32(text: String): UInt {
        val state = FxHasher32()
        val bytes = text.encodeToByteArray()
        state.write(bytes)
        state.writeU8(STR_TERMINATOR)
        return state.value
    }

    fun hash64(text: String): ULong {
        val state = FxHasher64()
        val bytes = text.encodeToByteArray()
        state.write(bytes)
        state.writeU8(STR_TERMINATOR.toULong())
        return state.value
    }

    fun hash(text: String): ULong = hash64(text)

    private class FxHasher32 {
        var value: UInt = 0u
            private set

        fun hashWord(word: UInt) {
            value = (value.rotateLeft(ROTATE) xor word) * SEED32
        }

        fun writeU8(i: UInt) = hashWord(i)

        fun write(bytes: ByteArray) {
            var i = 0
            while (bytes.size - i >= 4) {
                hashWord(readU32Le(bytes, i))
                i += 4
            }
            while (i < bytes.size) {
                hashWord((bytes[i].toInt() and 0xff).toUInt())
                i += 1
            }
        }
    }

    private class FxHasher64 {
        var value: ULong = 0uL
            private set

        fun hashWord(word: ULong) {
            value = (value.rotateLeft(ROTATE) xor word) * SEED64
        }

        fun writeU8(i: ULong) = hashWord(i)

        fun write(bytes: ByteArray) {
            var i = 0
            while (bytes.size - i >= 8) {
                hashWord(readU64Le(bytes, i))
                i += 8
            }
            if (bytes.size - i >= 4) {
                hashWord(readU32Le(bytes, i).toULong())
                i += 4
            }
            while (i < bytes.size) {
                hashWord((bytes[i].toInt() and 0xff).toULong())
                i += 1
            }
        }
    }

    private fun readU32Le(bytes: ByteArray, offset: Int): UInt {
        val b0 = (bytes[offset].toInt() and 0xff).toUInt()
        val b1 = (bytes[offset + 1].toInt() and 0xff).toUInt()
        val b2 = (bytes[offset + 2].toInt() and 0xff).toUInt()
        val b3 = (bytes[offset + 3].toInt() and 0xff).toUInt()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readU64Le(bytes: ByteArray, offset: Int): ULong {
        val lo = readU32Le(bytes, offset).toULong()
        val hi = readU32Le(bytes, offset + 4).toULong()
        return lo or (hi shl 32)
    }
}
