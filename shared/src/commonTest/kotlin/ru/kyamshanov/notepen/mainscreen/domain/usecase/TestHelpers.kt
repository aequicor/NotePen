package ru.kyamshanov.notepen.mainscreen.domain.usecase

import kotlinx.coroutines.runBlocking

/**
 * Test helper: runs a suspending block synchronously using runBlocking.
 * Allowed in tests per project code style.
 */
internal fun runTestBlocking(block: suspend () -> Unit) = runBlocking { block() }
