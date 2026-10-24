package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import app.cash.turbine.test
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.ReviewedPhotoEntity
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReviewedPhotoDaoTest {

    private val day = LocalDate.of(2026, 10, 12)
    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var dao: ReviewedPhotoDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.reviewedPhotoDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `count is zero without rows`() = runTest {
        assertEquals(0, dao.observeCount().first())
    }

    @Test
    fun `insert adds a row to the count`() = runTest {
        dao.insert(ReviewedPhotoEntity(1L, day))
        dao.insert(ReviewedPhotoEntity(2L, day))
        assertEquals(2, dao.observeCount().first())
    }

    @Test
    fun `inserting the same id again is ignored`() = runTest {
        dao.insert(ReviewedPhotoEntity(1L, day))
        dao.insert(ReviewedPhotoEntity(1L, day.plusYears(1)))
        assertEquals(1, dao.observeCount().first())
    }

    @Test
    fun `the count emits again after an insert`() = runTest {
        dao.observeCount().test {
            assertEquals(0, awaitItem())
            dao.insert(ReviewedPhotoEntity(5L, day))
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
