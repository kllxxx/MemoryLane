package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SwipeRepositoryImplTest {

    private val pendingDeleteDao = mockk<PendingDeleteDao>()
    private val swipeSessionDao = mockk<SwipeSessionDao>()
    private val repository = SwipeRepositoryImpl(pendingDeleteDao, swipeSessionDao)

    @Test
    fun `clearPendingDeletes clears every date`() = runTest {
        coEvery { pendingDeleteDao.clear() } returns Unit

        repository.clearPendingDeletes()

        coVerify(exactly = 1) { pendingDeleteDao.clear() }
        coVerify(exactly = 0) { pendingDeleteDao.clearByDate(any()) }
    }

    @Test
    fun `clearPendingDeletesByDate clears only that date`() = runTest {
        val date = LocalDate.of(2026, 10, 15)
        coEvery { pendingDeleteDao.clearByDate(date) } returns Unit

        repository.clearPendingDeletesByDate(date)

        coVerify(exactly = 1) { pendingDeleteDao.clearByDate(date) }
        coVerify(exactly = 0) { pendingDeleteDao.clear() }
    }
}
