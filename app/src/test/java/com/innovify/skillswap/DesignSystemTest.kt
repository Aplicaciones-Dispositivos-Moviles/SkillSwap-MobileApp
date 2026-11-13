package com.innovify.skillswap

import com.innovify.skillswap.shared.presentation.design.firstNameOf
import com.innovify.skillswap.shared.presentation.design.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignSystemTest {

    @Test
    fun initialsUseTheFirstTwoWordsOfTheName() {
        assertEquals("VR", initialsOf("Valeria Ramos Quispe"))
        assertEquals("EP", initialsOf("estudiante.prueba"))
        assertEquals("A", initialsOf("ana"))
        assertEquals("?", initialsOf("  "))
    }

    @Test
    fun firstNameFallsBackWhenThereIsNoFullName() {
        assertEquals("Valeria", firstNameOf("Valeria Ramos Quispe", "valeria.r"))
        assertEquals("valeria.r", firstNameOf(null, "valeria.r"))
        assertEquals("valeria.r", firstNameOf("   ", "valeria.r"))
    }
}
