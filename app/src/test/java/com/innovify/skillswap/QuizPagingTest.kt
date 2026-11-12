package com.innovify.skillswap

import com.innovify.skillswap.assessment.presentation.canSubmitQuiz
import com.innovify.skillswap.assessment.presentation.initialQuizPage
import com.innovify.skillswap.assessment.presentation.optionLetter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// One-question-per-page quiz helpers.
class QuizPagingTest {

    // ---------------------------------------------------------------- quiz paging

    @Test
    fun quizOpensOnTheFirstUnansweredQuestion() {
        assertEquals(0, initialQuizPage(listOf(-1, -1, -1)))
        assertEquals(2, initialQuizPage(listOf(1, 0, -1, -1)))
        assertEquals(3, initialQuizPage(listOf(1, 0, 2, 3)))
        assertEquals(0, initialQuizPage(emptyList()))
    }

    @Test
    fun quizCanBeSentOnlyWhenEveryQuestionIsAnswered() {
        assertTrue(canSubmitQuiz(listOf(0, 1, 2)))
        assertFalse(canSubmitQuiz(listOf(0, -1, 2)))
        assertFalse(canSubmitQuiz(emptyList()))
    }

    @Test
    fun quizOptionsAreLetteredFromA() {
        assertEquals(listOf("A", "B", "C", "D"), (0..3).map { optionLetter(it) })
    }
}
