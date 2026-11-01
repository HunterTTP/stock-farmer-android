package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PetTest {
    @Test fun `thereAreSixColors`() {
        assertEquals(listOf("grey", "black", "white", "orange", "calico", "colorpoint"), Cat.all.map { it.id })
        assertEquals(listOf("Grey", "Black", "White", "Orange", "Calico", "Colorpoint"), Cat.all.map { it.colorName })
    }
}
