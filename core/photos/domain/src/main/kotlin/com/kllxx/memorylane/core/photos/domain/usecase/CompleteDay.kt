package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.SessionCompletion
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Records that every photo of [day] has a decision: the session of the day is saved as completed, with what it had
 * freed so far, on the date the day is really completed on (see [SessionCompletion.completedAt]). A failure to read or
 * write is thrown to the caller.
 */
class CompleteDay @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(day: LocalDate) {
        val existing = swipeRepository.getCompletedSessions().first().firstOrNull { it.date == day }
        swipeRepository.saveSession(
            SwipeSession(
                date = day,
                completed = true,
                savedBytes = existing?.savedBytes ?: 0,
                completedAt = SessionCompletion.completedAt(existing, day, LocalDate.now(clock)),
                deletedCount = existing?.deletedCount ?: 0,
            ),
        )
    }
}
