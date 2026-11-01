package com.hunter.stockfarmer.core.catalog

internal val moduleDecorations =
    listOf(
        Decoration(
            id = "dressmakerDisplay",
            name = "Dressmaker Display",
            assetNames = listOf("Tailor_Man_Mannequin_48x48", "Tailor_Woman_Mannequin_48x48"),
            sizeNames = listOf("Shirt", "Dress"),
            width = 1,
            height = 1,
            cost = 40.0,
            unlockLevel = 19,
            effect = DecorationEffect.None,
            drawScale = 0.75,
        ),
    )
