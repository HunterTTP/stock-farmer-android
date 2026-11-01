package com.hunter.stockfarmer.core.persistence

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
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
}

class LocalSaveFileSystem : SaveFileSystem {
    override fun read(url: Path): ByteArray? =
        try {
            Files.readAllBytes(url)
        } catch (_: NoSuchFileException) {
            null
        }

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        val destination = to.toAbsolutePath()
        val temporary = Files.createTempFile(destination.parent, ".save-", ".tmp")
        try {
            Files.write(temporary, data)
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // Cleanup must not hide the original write failure.
            try {
                Files.deleteIfExists(temporary)
            } catch (_: IOException) {
                // An interrupted write can leave an unindexed temporary file.
            }
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
