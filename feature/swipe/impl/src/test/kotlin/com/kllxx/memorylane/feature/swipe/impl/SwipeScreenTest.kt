package com.kllxx.memorylane.feature.swipe.impl

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeEffect
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeIntent
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeViewModel
import com.kllxx.memorylane.feature.swipe.impl.swipe.ui.FixtureDate
import com.kllxx.memorylane.feature.swipe.impl.swipe.ui.swipeState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/** The stateful [SwipeScreen] with a fake ViewModel: navigation callbacks, share and open intents, the resume hook. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class SwipeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val deck = DeckKey.Day(FixtureDate)
    private val photoUri = Uri.parse("content://media/external/images/media/7")
    private val uiState = MutableStateFlow<SwipeState>(swipeState(count = 3))
    private val sent = Channel<SwipeEffect>(Channel.UNLIMITED)
    private val viewModel = mockk<SwipeViewModel>(relaxed = true) {
        every { state } returns uiState
        every { effects } returns sent.receiveAsFlow()
    }

    private val calls = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            MemoryLaneTheme {
                SwipeScreen(
                    onNavigateToBeforeDelete = { calls += "before-delete $it" },
                    onNavigateToResult = { d, kept, total -> calls += "result $d $kept $total" },
                    onNavigateToCalendar = { calls += "calendar" },
                    viewModel = viewModel,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun send(effect: SwipeEffect) {
        sent.trySend(effect)
        rule.waitForIdle()
    }

    private fun startedActivity(): Intent? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity

    // --- navigation ---

    @Test
    fun `a full deck with marks goes to before delete with its deck`() {
        show()

        send(SwipeEffect.NavigateToBeforeDelete(deck, totalPhotosCount = 9))

        assertEquals(listOf("before-delete $deck"), calls)
    }

    @Test
    fun `a deck with nothing marked goes to the result with the counts`() {
        show()

        send(SwipeEffect.NavigateToResult(deck, keptCount = 4, totalCount = 9))

        assertEquals(listOf("result $deck 4 9"), calls)
    }

    @Test
    fun `calendar is where an empty deck and a missing permission lead`() {
        show()

        send(SwipeEffect.NavigateToCalendar)
        send(SwipeEffect.PermissionRequired)

        assertEquals(listOf("calendar", "calendar"), calls)
    }

    // --- share and open ---

    @Test
    fun `share starts a chooser for the photo`() {
        show()

        send(SwipeEffect.ShareImage(photoUri))

        val chooser = startedActivity()
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/*", send.type)
        @Suppress("DEPRECATION")
        assertEquals(photoUri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("Поделиться фото", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
    }

    @Test
    fun `open in gallery starts a view of the photo`() {
        show()

        send(SwipeEffect.OpenInGallery(photoUri))

        val view = startedActivity()
        assertNotNull(view)
        assertEquals(Intent.ACTION_VIEW, view!!.action)
        assertEquals(photoUri, view.data)
        assertEquals("image/*", view.type)
    }

    @Test
    fun `without an app to open the photo the user gets a message and the screen stays`() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        show()

        send(SwipeEffect.OpenInGallery(photoUri))

        assertNull(startedActivity())
        assertEquals("Нет приложения, чтобы открыть фото", ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<String>(), calls)
    }

    // --- coming back ---

    @Test
    fun `coming back to a finished deck asks the ViewModel to return to it`() {
        uiState.value = swipeState(count = 2).copy(isFinished = true)

        show()

        verify(atLeast = 1) { viewModel.onIntent(SwipeIntent.ReturnedToDeck) }
    }

    @Test
    fun `an unfinished deck is not asked to return`() {
        uiState.value = swipeState(count = 2, decisions = mapOf(1L to PhotoDecision.KEEP))

        show()

        verify(exactly = 0) { viewModel.onIntent(SwipeIntent.ReturnedToDeck) }
    }
}
