package com.hunter.stockfarmer.core.persistence

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/** Save operations also allow deterministic storage failures in tests. */
interface SaveFileSystem {
    fun read(url: Path): ByteArray?

    fun write(
        data: ByteArray,
        to: Path,
    )

    fun remove(url: Path)

    fun createDirectory(url: Path)

    /** Asked of every empty slot on every save, so a missing file should cost no exception. */
    fun exists(url: Path): Boolean = runCatching { read(url) != null }.getOrDefault(false)
}

open class LocalSaveFileSystem : SaveFileSystem {
    override fun read(url: Path): ByteArray? =
        try {
            Files.readAllBytes(url)
        } catch (_: NoSuchFileException) {
            null
        }

    override fun exists(url: Path): Boolean = Files.exists(url)

    /** The old file stays whole until the new one is on the disk, so a crash or power cut at any step leaves one of them. */
    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        val destination = to.toAbsolutePath()
        val temporary = Files.createTempFile(destination.parent, ".save-", ".tmp")
        try {
            writeDurably(data, temporary)
            moveIntoPlace(temporary, destination)
            syncDirectory(destination.parent)
        } finally {
            // Cleanup must not hide the original write failure.
            try {
                Files.deleteIfExists(temporary)
            } catch (_: IOException) {
                // An interrupted write can leave an unindexed temporary file.
            }
        }
    }

    /** Without the sync, a rename can reach the disk before the data does and a power cut leaves an empty save. */
    protected open fun writeDurably(
        data: ByteArray,
        temporary: Path,
    ) {
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(data)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    protected open fun moveIntoPlace(
        temporary: Path,
        destination: Path,
    ) {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Makes the rename itself survive a power cut. Failing here is not a failed save: the new file is already in place and
     * whole, and at worst the old whole file comes back. Some systems, Windows among them, cannot open a directory at all.
     */
    protected open fun syncDirectory(directory: Path) {
        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // See above.
        }
    }

    override fun remove(url: Path) {
        try {
            Files.walkFileTree(
                url,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        Files.delete(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(
                        dir: Path,
                        exc: IOException?,
                    ): FileVisitResult {
                        if (exc != null) throw exc
                        Files.delete(dir)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (_: NoSuchFileException) {
            // Removing a missing save is already complete.
        }
    }

    override fun createDirectory(url: Path) {
        Files.createDirectories(url)
    }
}
