package com.hunter.stockfarmer.farm

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.GdxNativesLoader
import com.hunter.stockfarmer.farm.effects.ShadowImage
import com.hunter.stockfarmer.farm.effects.rgba
import com.hunter.stockfarmer.farm.effects.rgbaAlphaBytes
import com.hunter.stockfarmer.farm.effects.writeRgba
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.random.Random

/** Runs the real gdx2d natives, because what matters is what its `drawPixel` and `getPixel` put in memory. */
internal class GeneratedTextureTest {
    private val colors = IntArray(5 * 7) { Random(it).nextInt() }

    private fun Pixmap.bytes(): ByteArray {
        val copy = pixels.duplicate()
        copy.position(0)
        return ByteArray(copy.remaining()).also { copy.get(it) }
    }

    private fun drawn(
        width: Int,
        height: Int,
        pixel: (Int, Int) -> Int,
    ): ByteArray {
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        try {
            pixmap.setBlending(Pixmap.Blending.None)
            for (y in 0 until height) {
                for (x in 0 until width) pixmap.drawPixel(x, y, pixel(x, y))
            }
            return pixmap.bytes()
        } finally {
            pixmap.dispose()
        }
    }

    private fun written(
        width: Int,
        height: Int,
        write: (ByteBuffer) -> Unit,
    ): ByteArray {
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        try {
            write(pixmap.pixels)
            return pixmap.bytes()
        } finally {
            pixmap.dispose()
        }
    }

    @BeforeEach
    fun loadNatives() = GdxNativesLoader.load()

    @Test
    fun `filling memory gives the bytes drawPixel gave`() {
        val pixel = { x: Int, y: Int -> colors[y * 5 + x] }
        assertArrayEquals(drawn(5, 7, pixel), written(5, 7) { it.fillRgba(5, 7, pixel) })
    }

    @Test
    fun `a shadow written to memory matches the per-pixel draw, whatever the buffer's byte order`() {
        val image = ShadowImage(5, 7, 0.0, 0.0, FloatArray(35) { it / 34f })
        val expected = drawn(5, 7) { x, y -> rgba(0, 0, 0, (image.alpha[y * 5 + x] * 255).roundToInt()) }
        assertArrayEquals(expected, written(5, 7, image::writeRgba))
    }

    @Test
    fun `reading alpha from memory matches getPixel`() {
        val pixmap = Pixmap(5, 7, Pixmap.Format.RGBA8888)
        try {
            pixmap.pixels.fillRgba(5, 7) { x, y -> colors[y * 5 + x] }
            val expected = ByteArray(35) { (pixmap.getPixel(it % 5, it / 5) and 255).toByte() }
            assertArrayEquals(expected, rgbaAlphaBytes(pixmap.pixels, 5, 7))
        } finally {
            pixmap.dispose()
        }
    }

    @Test
    fun `each prepare runs the stored fill again and holds no pixels between uploads`() {
        var fills = 0
        val data =
            GeneratedTextureData(2, 3) {
                fills++
                it.fillRgba(2, 3) { x, y -> rgba(x, y, fills, 255) }
            }
        assertFalse(data.isPrepared)
        assertTrue(data.isManaged)
        assertTrue(data.disposePixmap())

        for (run in 1..2) {
            data.prepare()
            assertTrue(data.isPrepared)
            val pixmap = data.consumePixmap()
            assertFalse(data.isPrepared)
            assertEquals(run, fills)
            assertEquals(Pixmap.Format.RGBA8888, pixmap.format)
            assertEquals(rgba(1, 2, run, 255), pixmap.getPixel(1, 2))
            pixmap.dispose()
        }
    }

    @Test
    fun `a pixmap is not handed out twice`() {
        val data = GeneratedTextureData(1, 1) { }
        data.prepare()
        assertThrows(RuntimeException::class.java) { data.prepare() }
        data.consumePixmap().dispose()
        assertThrows(RuntimeException::class.java) { data.consumePixmap() }
    }

    @Test
    fun `a failing fill does not leave a half-made pixmap prepared`() {
        val data = GeneratedTextureData(1, 1) { error("no art") }
        assertThrows(IllegalStateException::class.java) { data.prepare() }
        assertFalse(data.isPrepared)
    }

    @Test
    fun `generated textures come back after a lost GL context and leave when released`() {
        withFakeGl { app ->
            var fills = 0
            val library = TextureLibrary()
            val before = Texture.getNumManagedTextures()
            library.generated("kept", 2, 2) { _, _ ->
                fills++
                -1
            }
            library.generated("released", 1, 1) { _, _ -> -1 }
            assertEquals(4, fills)
            assertEquals(before + 2, Texture.getNumManagedTextures())

            Texture.invalidateAllTextures(app)
            assertEquals(8, fills)

            library.releaseGenerated("released")
            assertEquals(before + 1, Texture.getNumManagedTextures())
            library.dispose()
        }
    }

    private fun withFakeGl(body: (Application) -> Unit) {
        val app = fake<Application>()
        val gl = fake<GL20>()
        val previous = Triple(Gdx.app, Gdx.gl, Gdx.graphics)
        Gdx.app = app
        Gdx.gl = gl
        Gdx.gl20 = gl
        Gdx.graphics = fake<Graphics>()
        try {
            body(app)
        } finally {
            Texture.clearAllTextures(app)
            Gdx.app = previous.first
            Gdx.gl = previous.second
            Gdx.gl20 = previous.second
            Gdx.graphics = previous.third
        }
    }

    private inline fun <reified T> fake(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "hashCode" -> {
                    System.identityHashCode(self)
                }

                "equals" -> {
                    self === args?.get(0)
                }

                "toString" -> {
                    "fake ${T::class.simpleName}"
                }

                else -> {
                    when (method.returnType) {
                        Int::class.javaPrimitiveType -> 1

                        // A live GL handle: libGDX skips disposing handle 0.
                        Boolean::class.javaPrimitiveType -> false

                        else -> null
                    }
                }
            }
        } as T
}
