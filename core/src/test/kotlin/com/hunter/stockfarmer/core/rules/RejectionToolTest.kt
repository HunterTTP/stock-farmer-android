package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.HarvestTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RejectionToolTest {
    @Test
    fun `a refusal that names an implement suggests it`() {
        assertEquals(HarvestTool.axe, Rejection.UseTheAxe.suggestedTool)
        assertEquals(HarvestTool.pickaxe, Rejection.UseThePickaxe.suggestedTool)
        assertEquals(HarvestTool.scythe, Rejection.UseTheScythe.suggestedTool)
        assertEquals(HarvestTool.bucket, Rejection.UseTheBucketForFruit.suggestedTool)
        assertEquals(HarvestTool.bucket, Rejection.UseTheBucketForEggs.suggestedTool)
        assertEquals(HarvestTool.bucket, Rejection.UseTheBucketForAnimals.suggestedTool)
        assertEquals(HarvestTool.bucket, Rejection.UseTheBucketForGoods.suggestedTool)
        assertEquals(HarvestTool.bucket, Rejection.GoodsWaiting.suggestedTool)
    }

    @Test
    fun `other refusals suggest nothing`() {
        assertNull(Rejection.NeedMoney(5.0).suggestedTool)
        assertNull(Rejection.NotEnoughSpace.suggestedTool)
    }

    @Test
    fun `every refusal that mentions an implement suggests it`() {
        val refusals =
            listOf(
                Rejection.UseTheAxe,
                Rejection.UseThePickaxe,
                Rejection.UseTheScythe,
                Rejection.UseTheBucketForFruit,
                Rejection.UseTheBucketForEggs,
                Rejection.UseTheBucketForAnimals,
                Rejection.UseTheBucketForGoods,
                Rejection.GoodsWaiting,
            )
        for (refusal in refusals) {
            val tool = refusal.suggestedTool
            assertTrue(tool != null && refusal.message.contains(tool.displayName), refusal.toString())
        }
    }
}
