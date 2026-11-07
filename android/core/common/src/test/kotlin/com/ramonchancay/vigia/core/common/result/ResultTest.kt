package com.ramonchancay.vigia.core.common.result

import app.cash.turbine.test
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultTest {
    @Test
    fun `asResult emits loading then success`() = runTest {
        flowOf(1).asResult().test {
            assertEquals(Result.Loading, awaitItem())
            assertEquals(Result.Success(1), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `asResult turns an upstream exception into error`() = runTest {
        val failure = IllegalStateException("boom")
        flow<Int> { throw failure }.asResult().test {
            assertEquals(Result.Loading, awaitItem())
            assertEquals(Result.Error(failure), awaitItem())
            awaitComplete()
        }
    }
}
