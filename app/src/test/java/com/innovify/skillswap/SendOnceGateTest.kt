package com.innovify.skillswap

import com.innovify.skillswap.assessment.application.SendOnceGate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendOnceGateTest {

    @Test
    fun concurrentSendsOfTheSameAttemptPostOnlyOnce() = runBlocking {
        val gate = SendOnceGate()
        var stored: String? = null
        var posts = 0

        val results = (1..2).map {
            async {
                gate.send(alreadyDone = { stored }) {
                    posts++
                    delay(50) // the network call
                    "graded".also { stored = it }
                }
            }
        }.awaitAll()

        assertEquals(1, posts)
        assertEquals(listOf("graded", "graded"), results)
    }

    @Test
    fun aFailedSendCanBeRetried() = runBlocking {
        val gate = SendOnceGate()
        var posts = 0

        assertNull(gate.send(alreadyDone = { null }) { posts++; null })
        assertEquals("graded", gate.send(alreadyDone = { null }) { posts++; "graded" })
        assertEquals(2, posts)
    }
}
