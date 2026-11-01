package com.hunter.stockfarmer.core.catalog

/** What a kind of livestock does for the farm once it is grown. */
sealed interface LivestockWork {
    /** Has something ready every so often that its tool takes from it where it stands. */
    data class Gives(
        val item: StorageItem,
        val tool: HarvestTool,
    ) : LivestockWork
}

/** One color of one kind of livestock: a sprite sheet for the grown animal and one for its baby. */
data class LivestockVariant(
    val id: String,
    val name: String,
    val assetName: String,
    val babyAssetName: String,
    /** Whether its sheet also holds the pictures of it with its wool taken off. */
    val hasShearedPictures: Boolean = false,
) {
    constructor(
        species: String,
        key: String,
        name: String,
        adult: String,
        baby: String,
        hasShearedPictures: Boolean = false,
    ) : this("$species:$key", name, "${adult}_48x48", "${baby}_48x48", hasShearedPictures)
}

/** A kind of farm animal that lives in a building, starts as a baby and grows up, and does some work for the farm. */
data class LivestockSpecies(
    val id: String,
    val name: String,
    val babyName: String,
    /** Where its pictures are, in `Images/animals`. */
    val folder: String,
    val growDuration: Double,
    /** What it gives now and then; null for an animal that gives nothing, like the duck. */
    val work: LivestockWork?,
    /** For the animals whose work is done now and then: how long between one time and the next. */
    val workInterval: Double,
    val variants: List<LivestockVariant>,
    /** What the player is paid to sell one of this kind; null for the animals that can only be released. */
    val salePrice: Double? = null,
) {
    val pluralName: String get() = if (id == "sheep") name else "${name}s"

    companion object {
        /** What a can of milk, of goat's milk, and a fleece sell for from storage. */
        const val MILK_SALE_PRICE = 60.0
        const val GOAT_MILK_SALE_PRICE = 18.0
        const val WOOL_SALE_PRICE = 30.0
        const val DUCK_SALE_PRICE = 25.0

        val rabbit =
            LivestockSpecies(
                id = "rabbit",
                name = "Rabbit",
                babyName = "Kit",
                folder = "Rabbits",
                growDuration = 8 * 60.0,
                work = LivestockWork.Gives(StorageItem.Fertilizer, HarvestTool.bucket),
                workInterval = 4 * 60.0,
                variants =
                    listOf(
                        LivestockVariant(
                            "rabbit",
                            "brown",
                            "Brown Rabbit",
                            adult = "Rabbit_Brown",
                            baby = "Rabbit_Baby_Brown",
                        ),
                        LivestockVariant(
                            "rabbit",
                            "darkEars",
                            "Dark Eared Rabbit",
                            adult = "Rabbit_Brown_Dark_Ears",
                            baby = "Rabbit_Baby_Brown",
                        ),
                        LivestockVariant(
                            "rabbit",
                            "gray",
                            "Gray Rabbit",
                            adult = "Rabbit_Gray",
                            baby = "Rabbit_Baby_Gray",
                        ),
                        LivestockVariant(
                            "rabbit",
                            "grayWhite",
                            "Gray and White Rabbit",
                            adult = "Rabbit_Gray_and_White",
                            baby = "Rabbit_Baby_Gray",
                        ),
                        LivestockVariant(
                            "rabbit",
                            "spotted",
                            "Spotted Rabbit",
                            adult = "Rabbit_Spotted",
                            baby = "Rabbit_Baby_Brown",
                        ),
                        LivestockVariant(
                            "rabbit",
                            "white",
                            "White Rabbit",
                            adult = "Rabbit_White",
                            baby = "Rabbit_Baby_White",
                        ),
                    ),
            )

        val duck =
            LivestockSpecies(
                id = "duck",
                name = "Duck",
                babyName = "Duckling",
                folder = "Ducks",
                growDuration = 10 * 60.0,
                work = null,
                workInterval = 0.0,
                salePrice = DUCK_SALE_PRICE,
                variants =
                    listOf(
                        LivestockVariant("duck", "brown", "Brown Duck", adult = "Duck_Brown", baby = "Duckling_Brown"),
                        LivestockVariant(
                            "duck",
                            "mallard",
                            "Mallard",
                            adult = "Duck_Green_Head",
                            baby = "Duckling_Brown",
                        ),
                        LivestockVariant(
                            "duck",
                            "redEyes",
                            "Red Eyed Duck",
                            adult = "Duck_Red_Eyes",
                            baby = "Duckling_Yellow",
                        ),
                        LivestockVariant("duck", "white", "White Duck", adult = "Duck_White", baby = "Duckling_Yellow"),
                    ),
            )

        val sheep =
            LivestockSpecies(
                id = "sheep",
                name = "Sheep",
                babyName = "Lamb",
                folder = "Sheeps",
                growDuration = 15 * 60.0,
                work = LivestockWork.Gives(StorageItem.Wool, HarvestTool.scythe),
                workInterval = 6 * 60.0,
                variants =
                    listOf(
                        LivestockVariant(
                            "sheep",
                            "black",
                            "Black Sheep",
                            adult = "Sheep_Black",
                            baby = "Sheep_Baby_Brown",
                        ),
                        LivestockVariant(
                            "sheep",
                            "brown",
                            "Brown Sheep",
                            adult = "Sheep_Brown",
                            baby = "Sheep_Baby_Brown",
                        ),
                        LivestockVariant(
                            "sheep",
                            "gray",
                            "Gray Sheep",
                            adult = "Sheep_Gray",
                            baby = "Sheep_Baby_White",
                        ),
                        LivestockVariant(
                            "sheep",
                            "white",
                            "White Sheep",
                            adult = "Sheep_White",
                            baby = "Sheep_Baby_White",
                        ),
                        LivestockVariant(
                            "sheep",
                            "yellow",
                            "Yellow Sheep",
                            adult = "Sheep_Yellow",
                            baby = "Sheep_Baby_White",
                        ),
                        LivestockVariant(
                            "sheep",
                            "woollyBlack",
                            "Woolly Black Sheep",
                            adult = "Sheep_2_Black",
                            baby = "Sheep_Baby_Brown",
                            hasShearedPictures = true,
                        ),
                        LivestockVariant(
                            "sheep",
                            "woollyBrown",
                            "Woolly Brown Sheep",
                            adult = "Sheep_2_Brown",
                            baby = "Sheep_Baby_Brown",
                            hasShearedPictures = true,
                        ),
                        LivestockVariant(
                            "sheep",
                            "woollyGray",
                            "Woolly Gray Sheep",
                            adult = "Sheep_2_Gray",
                            baby = "Sheep_Baby_White",
                            hasShearedPictures = true,
                        ),
                        LivestockVariant(
                            "sheep",
                            "woollyWhite",
                            "Woolly White Sheep",
                            adult = "Sheep_2_White",
                            baby = "Sheep_Baby_White",
                            hasShearedPictures = true,
                        ),
                        LivestockVariant(
                            "sheep",
                            "woollyYellow",
                            "Woolly Yellow Sheep",
                            adult = "Sheep_2_Yellow",
                            baby = "Sheep_Baby_White",
                            hasShearedPictures = true,
                        ),
                        LivestockVariant(
                            "sheep",
                            "woollyPaleYellow",
                            "Woolly Pale Yellow Sheep",
                            adult = "Sheep_2_Yellow_2",
                            baby = "Sheep_Baby_White",
                            hasShearedPictures = true,
                        ),
                    ),
            )

        val goat =
            LivestockSpecies(
                id = "goat",
                name = "Goat",
                babyName = "Kid",
                folder = "Goats",
                growDuration = 15 * 60.0,
                work = LivestockWork.Gives(StorageItem.GoatMilk, HarvestTool.bucket),
                workInterval = 5 * 60.0,
                variants =
                    listOf(
                        LivestockVariant(
                            "goat",
                            "blackHead",
                            "Black Headed Goat",
                            adult = "Goat_Black_Head",
                            baby = "Goat_Baby_White",
                        ),
                        LivestockVariant(
                            "goat",
                            "ginger",
                            "Ginger Goat",
                            adult = "Goat_Brown_2",
                            baby = "Goat_Baby_Brown",
                        ),
                        LivestockVariant("goat", "tan", "Tan Goat", adult = "Goat_Brown", baby = "Goat_Baby_Brown"),
                        LivestockVariant("goat", "white", "White Goat", adult = "Goat_White", baby = "Goat_Baby_White"),
                    ),
            )

        val pig =
            LivestockSpecies(
                id = "pig",
                name = "Pig",
                babyName = "Piglet",
                folder = "Pigs",
                growDuration = 20 * 60.0,
                work = LivestockWork.Gives(StorageItem.Fertilizer, HarvestTool.bucket),
                workInterval = 6 * 60.0,
                variants =
                    listOf(
                        LivestockVariant("pig", "pink", "Pink Pig", adult = "Pig_Pink", baby = "Piglet_Pink_Light"),
                        LivestockVariant(
                            "pig",
                            "lightPink",
                            "Light Pink Pig",
                            adult = "Pig_Pink_Light",
                            baby = "Piglet_Pink_Light",
                        ),
                        LivestockVariant(
                            "pig",
                            "pinkBrown",
                            "Pink and Brown Pig",
                            adult = "Pig_Pink_and_Brown",
                            baby = "Piglet_Pink_Light",
                        ),
                        LivestockVariant(
                            "pig",
                            "spottedGray",
                            "Gray Spotted Pig",
                            adult = "Pig_Spotted_Gray",
                            baby = "Piglet_Gray",
                        ),
                        LivestockVariant(
                            "pig",
                            "spottedPink",
                            "Pink Spotted Pig",
                            adult = "Pig_Spotted_Pink",
                            baby = "Piglet_Pink_Light",
                        ),
                    ),
            )

        val donkey =
            LivestockSpecies(
                id = "donkey",
                name = "Donkey",
                babyName = "Foal",
                folder = "Donkeys",
                growDuration = 25 * 60.0,
                work = LivestockWork.Gives(StorageItem.Fertilizer, HarvestTool.bucket),
                workInterval = 8 * 60.0,
                variants =
                    listOf(
                        LivestockVariant("donkey", "dark", "Dark Donkey", adult = "Donkey", baby = "Donkey_Baby_Brown"),
                        LivestockVariant(
                            "donkey",
                            "gray",
                            "Gray Donkey",
                            adult = "Donkey_Grey",
                            baby = "Donkey_Baby_Gray",
                        ),
                        LivestockVariant(
                            "donkey",
                            "caramel",
                            "Caramel Donkey",
                            adult = "Donkey_Caramel",
                            baby = "Donkey_Baby_Brown",
                        ),
                        LivestockVariant(
                            "donkey",
                            "lightBrown",
                            "Light Brown Donkey",
                            adult = "Donkey_Light_Brown",
                            baby = "Donkey_Baby_Brown",
                        ),
                    ),
            )

        val cow =
            LivestockSpecies(
                id = "cow",
                name = "Cow",
                babyName = "Calf",
                folder = "Cows",
                growDuration = 30 * 60.0,
                work = LivestockWork.Gives(StorageItem.Milk, HarvestTool.bucket),
                workInterval = 6 * 60.0,
                variants =
                    listOf(
                        LivestockVariant("cow", "blackSpotted", "Black Spotted Cow", adult = "Cow", baby = "Cow_Baby"),
                        LivestockVariant(
                            "cow",
                            "caramelSpotted",
                            "Caramel Spotted Cow",
                            adult = "Cow_Caramel",
                            baby = "Cow_Baby_Caramel",
                        ),
                        LivestockVariant(
                            "cow",
                            "bigBlack",
                            "Big Black Cow",
                            adult = "Cow_Big_Black",
                            baby = "Cow_Baby",
                        ),
                        LivestockVariant(
                            "cow",
                            "bigWhite",
                            "Big White Cow",
                            adult = "Cow_Big_White",
                            baby = "Cow_Baby",
                        ),
                        LivestockVariant(
                            "cow",
                            "bigCaramel",
                            "Big Caramel Cow",
                            adult = "Cow_Big_Caramel",
                            baby = "Cow_Baby_Caramel",
                        ),
                    ),
            )

        val all: List<LivestockSpecies> by lazy { listOf(rabbit, duck, sheep, goat, pig, donkey, cow) }

        /** The species and color a stored id stands for. */
        fun variant(id: String): Pair<LivestockSpecies, LivestockVariant>? {
            for (species in all) {
                val variant = species.variants.firstOrNull { it.id == id }
                if (variant != null) return species to variant
            }
            return null
        }
    }
}

/** Which animals a building holds, how many, and how often it has a new baby. */
data class LivestockHome(
    val buildingID: String,
    val species: List<LivestockSpecies>,
    /** How many it holds at Level 1; a building's upgrades hold more, and a pond's capacity is worked out from its shape. */
    val capacity: Int,
    val birthInterval: Double,
) {
    companion object {
        /** The rabbit hutch holds rabbits, the sheep, goat and pig barns their own animal, the stable donkeys and the luxury barn cows. */
        val all: List<LivestockHome> by lazy {
            fun home(
                buildingID: String,
                species: LivestockSpecies,
                minutes: Int,
            ) = LivestockHome(buildingID, listOf(species), HomeUpgrade.capacity(buildingID, 1), minutes * 60.0)
            with(LivestockSpecies) {
                listOf(
                    home("rabbitHutch", rabbit, 4),
                    home("barn1", sheep, 8),
                    home("goatBarn", goat, 8),
                    home("pigBarn", pig, 8),
                    home("stable1", donkey, 10),
                    home("barn2", cow, 10),
                )
            }
        }

        fun of(building: Building): LivestockHome? = all.firstOrNull { it.buildingID == building.id }
    }
}
