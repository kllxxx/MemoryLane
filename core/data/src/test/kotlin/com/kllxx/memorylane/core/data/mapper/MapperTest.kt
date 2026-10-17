package com.kllxx.memorylane.core.data.mapper

import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MapperTest {
    private val date = LocalDate.of(2024, 10, 15)

    @Test
    fun pendingDeleteRoundTrip() {
        val domain = PendingDelete(photoId = 42L, sessionDate = date)
        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun swipeSessionRoundTrip() {
        val domain = SwipeSession(date = date, completed = true, savedBytes = 1_024L, completedAt = date.plusDays(2))
        assertEquals(domain, domain.toEntity().toDomain())
    }
}
