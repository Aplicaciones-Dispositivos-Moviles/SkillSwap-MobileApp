package com.innovify.skillswap

import com.innovify.skillswap.viewmodel.SignUpValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpValidatorTest {

    @Test
    fun acceptsValidData() {
        assertNull(SignUpValidator.validate("victor", "u2020@upc.edu.pe", "clave1234", "clave1234"))
    }

    @Test
    fun rejectsNonInstitutionalEmail() {
        assertFalse(SignUpValidator.isInstitutionalEmail("victor@gmail.com"))
        assertTrue(SignUpValidator.isInstitutionalEmail("U2020@UPC.EDU.PE"))
        assertEquals(
            "Usa tu correo institucional (termina en .edu.pe)",
            SignUpValidator.validate("victor", "victor@gmail.com", "clave1234", "clave1234")
        )
    }

    @Test
    fun rejectsShortPasswordAndMismatch() {
        assertEquals(
            "La contraseña debe tener al menos 8 caracteres",
            SignUpValidator.validate("victor", "u2020@upc.edu.pe", "corta", "corta")
        )
        assertEquals(
            "Las contraseñas no coinciden",
            SignUpValidator.validate("victor", "u2020@upc.edu.pe", "clave1234", "clave12345")
        )
    }

    @Test
    fun rejectsBlankOrSpacedUsername() {
        assertEquals(
            "Escribe un nombre de usuario",
            SignUpValidator.validate("  ", "u2020@upc.edu.pe", "clave1234", "clave1234")
        )
        assertEquals(
            "El usuario no puede tener espacios",
            SignUpValidator.validate("vic tor", "u2020@upc.edu.pe", "clave1234", "clave1234")
        )
    }
}
