package com.hunter.stockfarmer.apk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Verifies Android 15+ 16 KB page-size compatibility for all native libraries:
 * 1. ELF PT_LOAD segment alignment: all arm64-v8a and x86_64 shared libraries (.so) have p_align >= 16384 (16 KB).
 * 2. APK packaging alignment: native libraries in the packaged APK are stored uncompressed (STORED)
 *    and zip-aligned to 16 KB (16384-byte) page boundaries so mmap can map them directly into memory.
 */
class NativeAlignmentTest {
    private val expected64BitAbis = setOf("arm64-v8a", "x86_64")
    private val pageAlignment16Kb = 16384L

    @Test
    fun `unpacked 64-bit native libraries have PT_LOAD segments aligned to at least 16 KB`() {
        val nativesDirs =
            listOf(
                File("build/natives"),
                File("app/build/natives"),
                File("../app/build/natives"),
            ).filter { it.isDirectory }

        assertTrue(nativesDirs.isNotEmpty(), "Native unpack directory must exist (run after assemble or unpackNatives)")

        var verifiedCount = 0
        val verifiedAbis = mutableSetOf<String>()

        for (baseDir in nativesDirs) {
            val soFiles = baseDir.walkTopDown().filter { it.isFile && it.extension == "so" }.toList()
            for (soFile in soFiles) {
                val abi = soFile.parentFile?.name.orEmpty()
                if (abi in expected64BitAbis) {
                    val alignments = ElfInspector.ptLoadAlignments(soFile.readBytes())
                    assertTrue(alignments.isNotEmpty(), "${soFile.path} must contain at least one PT_LOAD segment")
                    for (align in alignments) {
                        assertTrue(
                            align >= pageAlignment16Kb,
                            "${soFile.path} PT_LOAD segment alignment ($align) must be >= 16 KB ($pageAlignment16Kb)",
                        )
                    }
                    verifiedCount++
                    verifiedAbis.add(abi)
                }
            }
        }

        assertTrue(verifiedCount > 0, "At least one 64-bit .so file must be verified")
        assertTrue(
            verifiedAbis.containsAll(expected64BitAbis),
            "Expected native libraries for all 64-bit ABIs ($expected64BitAbis), but verified: $verifiedAbis",
        )
    }

    @Test
    fun `packaged APK stores native libraries uncompressed and aligned to 16 KB boundaries`() {
        val apkCandidates =
            listOf(
                File("build/outputs/apk/debug/app-debug.apk"),
                File("app/build/outputs/apk/debug/app-debug.apk"),
                File("../app/build/outputs/apk/debug/app-debug.apk"),
            )
        val apk = apkCandidates.firstOrNull { it.isFile } ?: return

        ZipFile(apk).use { zip ->
            val soEntries =
                zip
                    .entries()
                    .asSequence()
                    .filter { it.name.endsWith(".so") }
                    .toList()
            assertTrue(soEntries.isNotEmpty(), "APK must contain native libraries")

            for (entry in soEntries) {
                assertEquals(
                    ZipEntry.STORED,
                    entry.method,
                    "${entry.name} must be stored uncompressed (STORED) in the APK for mmap to work",
                )

                val abi = entry.name.split("/").getOrNull(1)
                if (abi in expected64BitAbis) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val alignments = ElfInspector.ptLoadAlignments(bytes)
                    assertTrue(alignments.isNotEmpty(), "${entry.name} must contain at least one PT_LOAD segment")
                    for (align in alignments) {
                        assertTrue(
                            align >= pageAlignment16Kb,
                            "${entry.name} PT_LOAD segment alignment ($align) must be >= 16 KB ($pageAlignment16Kb)",
                        )
                    }
                }
            }
        }

        val dataOffsets = ApkZipInspector.nativeLibraryDataOffsets(apk)
        assertTrue(dataOffsets.isNotEmpty(), "APK must yield data offsets for native libraries")
        for ((name, offset) in dataOffsets) {
            val remainder = offset % pageAlignment16Kb
            assertEquals(
                0L,
                remainder,
                "Native library $name payload at offset $offset in APK must be 16 KB aligned (offset % 16384 == 0)",
            )
        }
    }
}

/** Lightweight ELF binary inspector to read PT_LOAD program header alignments. */
internal object ElfInspector {
    private const val EI_NIDENT = 16
    private const val ELFCLASS64 = 2
    private const val ELFDATA2LSB = 1
    private const val PT_LOAD = 1

    fun ptLoadAlignments(bytes: ByteArray): List<Long> {
        if (bytes.size < EI_NIDENT) return emptyList()
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return emptyList()
        }
        val is64Bit = bytes[4].toInt() == ELFCLASS64
        val isLittleEndian = bytes[5].toInt() == ELFDATA2LSB
        if (!is64Bit || !isLittleEndian) return emptyList()

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val phOff = buffer.getLong(32)
        val phEntSize = buffer.getShort(54).toInt() and 0xFFFF
        val phNum = buffer.getShort(56).toInt() and 0xFFFF

        val alignments = mutableListOf<Long>()
        for (i in 0 until phNum) {
            val offset = (phOff + i * phEntSize).toInt()
            if (offset + phEntSize <= bytes.size) {
                val pType = buffer.getInt(offset)
                if (pType == PT_LOAD) {
                    val pAlign = buffer.getLong(offset + 48)
                    alignments.add(pAlign)
                }
            }
        }
        return alignments
    }
}

/** Parses ZIP central directory and local headers to compute exact byte offsets of file payloads in the APK. */
internal object ApkZipInspector {
    fun nativeLibraryDataOffsets(apkFile: File): Map<String, Long> {
        RandomAccessFile(apkFile, "r").use { raf ->
            val fileLength = raf.length()
            val eocdOffset = findEocd(raf, fileLength)
            raf.seek(eocdOffset + 10)
            val totalEntries = readShort(raf)
            val cdOffset = readInt(raf, eocdOffset + 16)

            val offsets = mutableMapOf<String, Long>()
            var pos = cdOffset
            for (i in 0 until totalEntries) {
                raf.seek(pos)
                val signature = readInt(raf)
                if (signature != 0x02014b50L) break

                raf.seek(pos + 28)
                val fnLen = readShort(raf)
                val extraLen = readShort(raf)
                val commentLen = readShort(raf)
                val localHeaderOffset = readInt(raf, pos + 42)

                val nameBytes = ByteArray(fnLen)
                raf.seek(pos + 46)
                raf.readFully(nameBytes)
                val name = String(nameBytes, Charsets.UTF_8)

                if (name.endsWith(".so")) {
                    raf.seek(localHeaderOffset + 26)
                    val locFnLen = readShort(raf)
                    val locExtraLen = readShort(raf)
                    val dataOffset = localHeaderOffset + 30 + locFnLen + locExtraLen
                    offsets[name] = dataOffset
                }

                pos += 46 + fnLen + extraLen + commentLen
            }
            return offsets
        }
    }

    private fun findEocd(
        raf: RandomAccessFile,
        fileLength: Long,
    ): Long {
        val searchLen = minOf(fileLength, 65536L + 22L).toInt()
        val buffer = ByteArray(searchLen)
        val readStart = fileLength - searchLen
        raf.seek(readStart)
        raf.readFully(buffer)

        for (i in searchLen - 22 downTo 0) {
            if (buffer[i] == 0x50.toByte() && buffer[i + 1] == 0x4B.toByte() &&
                buffer[i + 2] == 0x05.toByte() && buffer[i + 3] == 0x06.toByte()
            ) {
                return readStart + i
            }
        }
        error("End of Central Directory record not found in APK")
    }

    private fun readShort(raf: RandomAccessFile): Int = (raf.readUnsignedByte() or (raf.readUnsignedByte() shl 8))

    private fun readInt(raf: RandomAccessFile): Long {
        val b0 = raf.readUnsignedByte().toLong()
        val b1 = raf.readUnsignedByte().toLong()
        val b2 = raf.readUnsignedByte().toLong()
        val b3 = raf.readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readInt(
        raf: RandomAccessFile,
        offset: Long,
    ): Long {
        raf.seek(offset)
        return readInt(raf)
    }
}
