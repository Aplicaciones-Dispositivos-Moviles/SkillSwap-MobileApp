package com.innovify.skillswap.assessment.application

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes sends that may race for the same item (the open quiz retries when internet returns while the
 * navigation resends every pending quiz). Inside the lock it first asks [alreadyDone] for a stored result, so
 * the second caller reuses the first one's result instead of posting the same attempt twice.
 */
class SendOnceGate {

    private val mutex = Mutex()

    suspend fun <T : Any> send(alreadyDone: suspend () -> T?, send: suspend () -> T?): T? =
        mutex.withLock { alreadyDone() ?: send() }
}
