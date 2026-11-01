package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FenceTest {
    @Test fun `aLoneFenceIsAPostAndHasNoArms`() {
        val lone = FenceConnections(false, false, false, false)
        assertEquals(listOf(FencePiece.unconnectedPost), lone.centerPieces)
        assertTrue(lone.arms.isEmpty())
    }

    @Test fun `runsUseEndcapsAndMiddlePieces`() {
        val cases =
            listOf(
                FenceConnections(false, true, false, false) to FencePiece.leftEndcap,
                FenceConnections(false, false, false, true) to FencePiece.rightEndcap,
                FenceConnections(false, true, false, true) to FencePiece.middleHorizontal,
                FenceConnections(true, false, false, false) to FencePiece.bottomEndcap,
                FenceConnections(false, false, true, false) to FencePiece.topEndcap,
                FenceConnections(true, false, true, false) to FencePiece.middleVertical,
            )
        cases.forEach { (connections, piece) -> assertEquals(listOf(piece), connections.centerPieces) }
    }

    @Test fun `cornersUseTheirOwnPicture`() {
        val cases =
            listOf(
                FenceConnections(false, true, true, false) to FencePiece.topLeftCorner,
                FenceConnections(false, false, true, true) to FencePiece.topRightCorner,
                FenceConnections(true, true, false, false) to FencePiece.bottomLeftCorner,
                FenceConnections(true, false, false, true) to FencePiece.bottomRightCorner,
            )
        cases.forEach { (connections, piece) -> assertEquals(listOf(piece), connections.centerPieces) }
    }

    @Test fun `junctionsLayerAHorizontalAndAVerticalPiece`() {
        assertEquals(
            listOf(FencePiece.middleHorizontal, FencePiece.topEndcap),
            FenceConnections(false, true, true, true).centerPieces,
        )
        assertEquals(
            listOf(FencePiece.leftEndcap, FencePiece.middleVertical),
            FenceConnections(true, true, true, false).centerPieces,
        )
        assertEquals(
            listOf(FencePiece.middleHorizontal, FencePiece.middleVertical),
            FenceConnections(true, true, true, true).centerPieces,
        )
    }

    @Test fun `armsReachTowardEachConnectedNeighbor`() {
        val all = FenceConnections(true, true, true, true)
        assertEquals(4, all.arms.size)
        assertTrue(FencePlacement(FencePiece.middleHorizontal, 1, 0) in all.arms)
        assertTrue(FencePlacement(FencePiece.middleVertical, 0, -1) in all.arms)
        assertEquals(
            listOf(FencePlacement(FencePiece.middleHorizontal, 1, 0)),
            FenceConnections(false, true, false, false).arms,
        )
    }

    @Test fun `connectionsComeFromTheNeighborsOnTheMap`() {
        val fences = listOf(GridPoint(10, 10), GridPoint(10, 11), GridPoint(11, 10)).associateWith { "wood" }
        assertEquals(FenceConnections(false, true, true, false), FenceConnections(GridPoint(10, 10), fences))
    }
}
