package com.kllxx.memorylane.core.data.mapper

import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MapperTest {
    private val date = LocalDate.of(2024, 10, 15)

    @Test
    fun `every decision is read back from its stored name`() {
        PhotoDecision.entries.forEach { decision ->
            val entity = PhotoDecisionEntity(photoId = 42L, sessionDate = date, decision = decision.name, decidedAt = 1)
            assertEquals(decision, entity.toDecisionOrNull())
        }
    }

    @Test
    fun `an unknown stored decision is null`() {
        val entity = PhotoDecisionEntity(photoId = 42L, sessionDate = date, decision = "ARCHIVED", decidedAt = 1)
        assertNull(entity.toDecisionOrNull())
    }

    @Test
    fun swipeSessionRoundTrip() {
        val domain = SwipeSession(
            date = date, completed = true, savedBytes = 1_024L, completedAt = date.plusDays(2), deletedCount = 12,
        )
        assertEquals(domain, domain.toEntity().toDomain())
    }
}
