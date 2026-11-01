package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.rules.Butterflies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class MenuPicturesTest {
    private val assets = File("src/main/assets")

    @Test
    fun `all catalog variants and stored item pictures resolve to bundled assets or drawn pictures`() {
        val state = unlockedMenuFarm()
        val choices = Menu.entries.flatMap { MenuState.from(state, it).sections.flatMap { section -> section.choices } }
        val pictures =
            choices.flatMap { listOf(it.picture) + it.variants.map { variant -> variant.picture } } +
                StorageItem.all.map(MenuPictures::stored)
        pictures.forEach { picture ->
            when (picture) {
                is MenuPicture.Asset -> assertTrue(File(assets, picture.path).isFile, picture.path)
                is MenuPicture.Symbol -> assertTrue(picture.text.isNotBlank())
                is MenuPicture.Ground -> assertTrue(File(assets, MenuPictures.terrain("grass").path).isFile)
                is MenuPicture.Water, is MenuPicture.Butterfly -> Unit
            }
        }
    }

    @Test
    fun `chicken pictures use an adult frame from a four direction sprite sheet`() {
        ChickenVariant.available.forEach { variant ->
            val picture = MenuPictures.stored(StorageItem.ChickenItem(variant)) as MenuPicture.Asset
            assertTrue(picture.chickenFrame)
            val sheet = ImageIO.read(File(assets, picture.path))
            assertTrue(sheet.width >= 4 * 48)
            assertEquals(0, sheet.height % 4)
            assertTrue(sheet.height / 4 > 0)
        }
    }

    @Test
    fun `fruit and rare crop icons use the same harvested produce art`() {
        Crop.all.forEach { crop ->
            val picture = MenuPictures.stored(StorageItem.CropItem(crop)) as MenuPicture.Asset
            assertEquals(picture, MenuPictures.stored(StorageItem.RareCrop(crop)))
            assertTrue(picture.path.endsWith(if (crop.isFruitTree) "${crop.basketAssetName}.png" else "-phase-4.png"))
        }
        assertEquals(
            MenuPicture.Butterfly(Butterflies.Wings.Shifting),
            MenuPictures.stored(StorageItem.Butterfly(Butterflies.Wings.Shifting)),
        )
    }
}
