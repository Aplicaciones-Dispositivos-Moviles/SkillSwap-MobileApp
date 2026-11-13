package com.innovify.skillswap

import com.innovify.skillswap.assessment.presentation.quizUnavailableMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizUnavailableMessageTest {

    @Test
    fun offlineSaysThatInternetIsNeeded() {
        assertEquals(
            "Necesitas conexión a internet para abrir este quiz. Conéctate y vuelve a intentarlo.",
            quizUnavailableMessage(isOnline = false)
        )
    }

    @Test
    fun onlineKeepsTheGenericMessage() {
        assertEquals("No se pudo abrir el quiz de este nodo.", quizUnavailableMessage(isOnline = true))
    }
}
