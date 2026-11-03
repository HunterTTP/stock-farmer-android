@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.GridPoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

data class TreeColor(
    val id: String,
    val name: String,
    internal val fileToken: String,
) {
    companion object {
        val brown = TreeColor("brown", "Brown", "Brown")
        val yellow = TreeColor("yellow", "Yellow", "Yellow")
        val green = TreeColor("green", "Green", "Green")
        val blueGreen = TreeColor("blueGreen", "Blue Green", "Blue_Green")
        val blue = TreeColor("blue", "Blue", "Blue")
        val darkGreen = TreeColor("darkGreen", "Dark Green", "Dark_Green")
    }
}

data class TreeSpecies(
    val id: String,
    val name: String,
    val colors: List<TreeColor>,
) {
    val trees: List<Tree> get() = Tree.all.filter { it.species == this }.sortedBy { it.size.order }
    val smallest: Tree get() = trees[0]
    val unlockLevel: Int get() = trees.minOfOrNull { it.unlockLevel } ?: 1

    companion object {
        val pine =
            TreeSpecies(
                "pine",
                "Pine",
                listOf(TreeColor.green, TreeColor.darkGreen, TreeColor.blue, TreeColor.blueGreen),
            )
        val oak = TreeSpecies("oak", "Oak", listOf(TreeColor.green, TreeColor.brown, TreeColor.blueGreen))
        val birch = TreeSpecies("birch", "Birch", listOf(TreeColor.green, TreeColor.brown, TreeColor.yellow))
        val all = listOf(pine, oak, birch)

        fun named(id: String): TreeSpecies? = all.firstOrNull { it.id == id }
    }
}

enum class TreeSize {
    small,
    medium,
    big,
    huge,
    ;

    val rawValue: String get() = name
    val displayName: String get() = name.replaceFirstChar { it.uppercaseChar() }
    val order: Int get() = ordinal
}

data class Tree(
    val species: TreeSpecies,
    val size: TreeSize,
    val side: Int,
    val cost: Double,
    val unlockLevel: Int,
) {
    val id: String get() = species.id + "-" + size.rawValue
    val hitsToFell: Int get() = 3 + size.order
    val logs: Int get() = hitsToFell
    val next: Tree?
        get() {
            val sizes = species.trees
            val index = sizes.indexOf(this)
            return if (index < 0) null else sizes.getOrNull(index + 1)
        }

    fun trunk(at: GridPoint): GridPoint = GridPoint(at.row + side - 1, at.col + (side - 1) / 2)

    val name: String get() = size.displayName + " " + species.name

    fun color(preferring: String): TreeColor = species.colors.firstOrNull { it.id == preferring } ?: species.colors[0]

    fun assetName(color: TreeColor): String {
// One of the pictures was saved with a doubled underscore.
        val token =
            if (species == TreeSpecies.pine && color == TreeColor.blueGreen &&
                size == TreeSize.medium
            ) {
                "Blue__Green"
            } else {
                color.fileToken
            }
        return "Tree_" + species.name + "_" + token + "_" + size.displayName + "_48x48"
    }

    fun footprint(at: GridPoint): List<GridPoint> = at.rectangle(side, side)

    fun origin(settingTrunkOn: GridPoint): GridPoint = settingTrunkOn.topLeftOfSquare(side)

    companion object {
        val logSalePrice = 0.0
        val growthInterval = 7200.0
        val all =
            listOf(
                Tree(TreeSpecies.pine, TreeSize.small, 1, 0.0, 1),
                Tree(TreeSpecies.pine, TreeSize.medium, 2, 0.0, 1),
                Tree(TreeSpecies.oak, TreeSize.small, 2, 0.0, 1),
                Tree(TreeSpecies.pine, TreeSize.big, 2, 0.0, 1),
                Tree(TreeSpecies.oak, TreeSize.medium, 2, 0.0, 1),
                Tree(TreeSpecies.pine, TreeSize.huge, 2, 0.0, 1),
                Tree(TreeSpecies.oak, TreeSize.big, 2, 0.0, 1),
                Tree(TreeSpecies.oak, TreeSize.huge, 3, 0.0, 1),
                Tree(TreeSpecies.birch, TreeSize.small, 2, 0.0, 1),
                Tree(TreeSpecies.birch, TreeSize.medium, 2, 0.0, 1),
                Tree(TreeSpecies.birch, TreeSize.big, 2, 0.0, 1),
                Tree(TreeSpecies.birch, TreeSize.huge, 2, 0.0, 1),
            )

        /** Decorative species do not change existing forests or regrowth. */
        val wild = all.filter { it.species == TreeSpecies.pine || it.species == TreeSpecies.oak }
        val starter = all[0]

        private val byID = all.associateBy { it.id }

        fun named(id: String): Tree? = byID[id]
    }
}

@Serializable(with = PlacedTreeSerializer::class)
data class PlacedTree(
    val treeID: String,
    val colorID: String,
    val grewAt: Instant,
    val isNaturallyGenerated: Boolean = true,
) {
    constructor(
        tree: Tree,
        color: TreeColor,
        grewAt: Instant,
        isNaturallyGenerated: Boolean = true,
    ) : this(tree.id, color.id, grewAt, isNaturallyGenerated)

    val tree: Tree get() = Tree.named(treeID) ?: Tree.starter
    val color: TreeColor get() = tree.color(colorID)
}

/** Old trees start their growth interval when decoded; the clock can be supplied for deterministic checks. */
class PlacedTreeSerializer() : KSerializer<PlacedTree> {
    private var now: () -> Instant = { Instant.now() }
    constructor(now: () -> Instant) : this() {
        this.now = now
    }

    @Serializable
    private data class Saved(
        val treeID: String,
        val colorID: String,
        val grewAt: String? = null,
        val isNaturallyGenerated: Boolean? = null,
    )

    override val descriptor = Saved.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: PlacedTree,
    ) = encoder.encodeSerializableValue(
        Saved.serializer(),
        Saved(value.treeID, value.colorID, value.grewAt.toString(), value.isNaturallyGenerated),
    )

    override fun deserialize(decoder: Decoder): PlacedTree {
        val saved = decoder.decodeSerializableValue(Saved.serializer())
        return PlacedTree(
            saved.treeID,
            saved.colorID,
            saved.grewAt?.let(Instant::parse) ?: now(),
            saved.isNaturallyGenerated ?: true,
        )
    }
}
