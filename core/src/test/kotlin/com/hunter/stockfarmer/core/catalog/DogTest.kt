package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DogTest {
    @Test fun `thereAreNineDogsInThreeBreeds`() {
        assertEquals(9, Dog.all.size)
        assertEquals(
            9,
            Dog.all
                .map { it.id }
                .toSet()
                .size,
        )
        assertTrue(DogBreed.entries.all { it.dogs.size == 3 })
        assertEquals("Gray Basenji", Dog.named("basenji-gray")?.name)
        assertEquals((0 until 9).toList(), Dog.all.map { it.doghouseRow }.sorted())
    }
}
