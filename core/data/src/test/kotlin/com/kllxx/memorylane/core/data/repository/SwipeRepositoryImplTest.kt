package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.DeletionDao
import com.kllxx.memorylane.core.data.db.dao.PhotoDecisionDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import com.kllxx.memorylane.core.data.mapper.toDomain
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeRepositoryImplTest {

    private val photoDecisionDao = mockk<PhotoDecisionDao>()
    private val swipeSessionDao = mockk<SwipeSessionDao>()
    private val deletionDao = mockk<DeletionDao>()
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = SwipeRepositoryImpl(photoDecisionDao, swipeSessionDao, deletionDao, clock)
    private val date = LocalDate.of(2026, 10, 15)

    @Test
    fun `saveDecision stores the decision with the day and the time of the decision`() = runTest {
        val saved = slot<PhotoDecisionEntity>()
        coEvery { photoDecisionDao.upsert(capture(saved)) } returns Unit

        repository.saveDecision(7L, date, PhotoDecision.DELETE)

        assertEquals(PhotoDecisionEntity(7L, date, "DELETE", now.toEpochMilli()), saved.captured)
    }

    @Test
    fun `getDecisionsByDate reads only that date and maps to a map by photo`() = runTest {
        coEvery { photoDecisionDao.getByDate(date) } returns listOf(
            PhotoDecisionEntity(7L, date, "KEEP", 1),
            PhotoDecisionEntity(8L, date, "TRASHED", 1),
        )

        assertEquals(
            mapOf(7L to PhotoDecision.KEEP, 8L to PhotoDecision.TRASHED),
            repository.getDecisionsByDate(date),
        )

        coVerify(exactly = 0) { photoDecisionDao.getAll() }
    }

    @Test
    fun `getDecisionsByDate ignores a decision this version does not know`() = runTest {
        coEvery { photoDecisionDao.getByDate(date) } returns listOf(
            PhotoDecisionEntity(7L, date, "KEEP", 1),
            PhotoDecisionEntity(8L, date, "FROM_THE_FUTURE", 1),
        )

        assertEquals(mapOf(7L to PhotoDecision.KEEP), repository.getDecisionsByDate(date))
    }

    @Test
    fun `removeDecision deletes only that photo`() = runTest {
        coEvery { photoDecisionDao.deleteById(7L) } returns Unit

        repository.removeDecision(7L)

        coVerify(exactly = 1) { photoDecisionDao.deleteById(7L) }
    }

    @Test
    fun `setDecisions updates the listed photos with the time of the change`() = runTest {
        coEvery { photoDecisionDao.updateDecisions(any(), any(), any()) } returns Unit

        repository.setDecisions(listOf(1L, 2L), PhotoDecision.TRASHED)

        coVerify(exactly = 1) { photoDecisionDao.updateDecisions(listOf(1L, 2L), "TRASHED", now.toEpochMilli()) }
    }

    @Test
    fun `setDecisions splits a long list under the SQLite variable limit`() = runTest {
        coEvery { photoDecisionDao.updateDecisions(any(), any(), any()) } returns Unit

        repository.setDecisions((1L..1_200L).toList(), PhotoDecision.DELETED)

        coVerify(exactly = 3) { photoDecisionDao.updateDecisions(match { it.size <= 500 }, "DELETED", any()) }
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
    fun `confirmDeletion hands the photos, the decision name and the time to the transaction`() = runTest {
        val bytes = mapOf(7L to 100L, 8L to 200L)
        coEvery {
            deletionDao.confirmDeletion(date, bytes, "TRASHED", now.toEpochMilli(), LocalDate.of(2026, 10, 28))
        } returns com.kllxx.memorylane.core.domain.model.DeletionOutcome(2, 300)

        val outcome = repository.confirmDeletion(date, bytes, PhotoDecision.TRASHED)

        assertEquals(com.kllxx.memorylane.core.domain.model.DeletionOutcome(2, 300), outcome)
    }
}
