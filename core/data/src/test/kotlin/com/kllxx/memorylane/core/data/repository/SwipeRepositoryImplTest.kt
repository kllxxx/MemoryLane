package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.PendingDeleteEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.data.mapper.toDomain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeRepositoryImplTest {

    private val pendingDeleteDao = mockk<PendingDeleteDao>()
    private val swipeSessionDao = mockk<SwipeSessionDao>()
    private val repository = SwipeRepositoryImpl(pendingDeleteDao, swipeSessionDao)

    @Test
    fun `getPendingDeletesByDate reads only that date and maps to domain`() = runTest {
        val date = LocalDate.of(2026, 10, 15)
        coEvery { pendingDeleteDao.getByDate(date) } returns listOf(PendingDeleteEntity(7L, date))

        assertEquals(listOf(PendingDelete(7L, date)), repository.getPendingDeletesByDate(date))

        coVerify(exactly = 0) { pendingDeleteDao.getAll() }
    }

    @Test
    fun `removePendingDelete deletes only that photo`() = runTest {
        coEvery { pendingDeleteDao.deleteByPhotoId(7L) } returns Unit

        repository.removePendingDelete(7L)

        coVerify(exactly = 1) { pendingDeleteDao.deleteByPhotoId(7L) }
    }

    @Test
    fun `clearPendingDeletesByDate clears only that date`()= runTest {
        val date = LocalDate.of(2026, 10, 15)
        coEvery { pendingDeleteDao.clearByDate(date) } returns Unit

        repository.clearPendingDeletesByDate(date)

        coVerify(exactly = 1) { pendingDeleteDao.clearByDate(date) }
    }

    @Test
    fun `getSessionByCompletedAt maps the completed session of that day`() = runTest {
        val day = LocalDate.of(2026, 10, 20)
        val entity = SwipeSessionEntity(LocalDate.of(2026, 10, 15), completed = true, savedBytes = 9, completedAt = day)
        coEvery { swipeSessionDao.getCompletedOn(day) } returns entity

        assertEquals(entity.toDomain(), repository.getSessionByCompletedAt(day))
    }

    @Test
    fun `getSessionByCompletedAt is null when nothing was completed that day`() = runTest {
        val day = LocalDate.of(2026, 10, 20)
        coEvery { swipeSessionDao.getCompletedOn(day) } returns null

        assertNull(repository.getSessionByCompletedAt(day))
    }

    @Test
    fun `cleanupStalePendingDeletes clears the pending deletes of completed sessions`() = runTest {
        coEvery { pendingDeleteDao.clearForCompletedSessions() } returns Unit

        repository.cleanupStalePendingDeletes()

        coVerify(exactly = 1) { pendingDeleteDao.clearForCompletedSessions() }
    }
}
