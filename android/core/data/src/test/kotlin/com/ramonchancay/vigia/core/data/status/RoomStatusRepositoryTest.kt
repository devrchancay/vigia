package com.ramonchancay.vigia.core.data.status

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStatusRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        StatusDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()
    private val repository = RoomStatusRepository(database.statusDao())

    @After
    fun tearDown() = database.close()

    @Test
    fun `change time moves only when the state flips`() = runTest {
        val url = "https://example.com"
        repository.recordUptime(url, up = true, statusCode = 200, latencyMs = 80, error = null, checkedAtMillis = 1_000)
        repository.recordUptime(url, up = true, statusCode = 200, latencyMs = 90, error = null, checkedAtMillis = 2_000)
        assertEquals(1_000L, repository.uptime.first().getValue(url).changedAtMillis)

        repository.recordUptime(
            url,
            up = false,
            statusCode = 503,
            latencyMs = 40,
            error = null,
            checkedAtMillis = 3_000
        )
        val down = repository.uptime.first().getValue(url)
        assertEquals(3_000L, down.changedAtMillis)
        assertEquals(503, down.statusCode)
    }

    @Test
    fun `retain drops sources nobody watches`() = runTest {
        repository.recordUptime("https://a.example", true, 200, 10, null, 1)
        repository.recordUptime("https://b.example", true, 200, 10, null, 1)
        val key = WorkflowRunStatus.keyOf("owner/repo", "main", null)
        repository.recordWorkflow(
            WorkflowRunStatus(key = key, repo = "owner/repo", status = "completed", checkedAtMillis = 1)
        )

        repository.retainOnly(urls = setOf("https://a.example"), workflowKeys = emptySet())

        assertEquals(setOf("https://a.example"), repository.uptime.first().keys)
        assertTrue(repository.workflows.first().isEmpty())
    }
}
