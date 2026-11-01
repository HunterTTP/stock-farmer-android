package com.hunter.stockfarmer.core.catalog

data class Cat(
    val id: String,
    val name: String,
    val assetName: String,
) {
    val colorName: String get() = name.replace(" Cat", "")

    companion object {
        val all =
            listOf(
                Cat("grey", "Grey Cat", "grey_cat"),
                Cat("black", "Black Cat", "black_cat"),
                Cat("white", "White Cat", "white_cat"),
                Cat("orange", "Orange Cat", "orange_cat"),
                Cat("calico", "Calico Cat", "calico_cat"),
                Cat("colorpoint", "Colorpoint Cat", "colorpoint_cat"),
            )
        val starter = all[0]

        fun named(id: String): Cat? = all.firstOrNull { it.id == id }
    }
}
