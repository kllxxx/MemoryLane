package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.haroncode.lazycardstack.LazyCardStack
import com.haroncode.lazycardstack.LazyCardStackState
import com.haroncode.lazycardstack.rememberLazyCardStackState
import com.haroncode.lazycardstack.swiper.ComposeWindow
import com.haroncode.lazycardstack.swiper.LocalComposeWindow
import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.ui.component.PreloadPhoto
import com.kllxx.memorylane.core.ui.component.PreloadPhotos
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.swipe.DeckPhoto
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeIntent
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.CardSize
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.PROVISIONAL_ASPECT_RATIO
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.aspectRatioOrNull
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.fitInside
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// How many cards ahead of the current one have their photo decoded before they are needed
private const val PRELOAD_AHEAD = 2

// A decoded size that differs from the known one by less than this is the same shape, rounded differently
private const val ASPECT_TOLERANCE = 0.01f

/**
 * The stack of cards, kept in step with [SwipeState]: the state's `currentIndex` is the truth and the stack only
 * ever follows it (see [planMove]). A swipe, whether by gesture, by the buttons or by an accessibility action,
 * becomes one `Swipe` intent through [SwipeGate]; the stack then already shows the next card.
 *
 * Every card has the shape of its photo and is centred in the card area: the container minus the inset on each
 * side, and the room above and below it (see [SwipeLayout]).
 */
@Composable
internal fun PhotoCardStack(
    state: SwipeState,
    onIntent: (SwipeIntent) -> Unit,
    modifier: Modifier = Modifier,
    // A parameter so that a test can watch which card the stack shows
    stack: LazyCardStackState = rememberLazyCardStackState(
        firstVisibleItemIndex = state.currentIndex.coerceIn(0, state.deck.lastIndex.coerceAtLeast(0)),
    ),
) {
    val latest by rememberUpdatedState(state)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val gate = remember { SwipeGate() }
    var syncTick by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(state) }
    var busy by remember { mutableStateOf(false) }
    // The shape of the photos MediaStore has no size for, found out from the decoded image
    val decodedAspects = remember { mutableStateMapOf<Long, Float>() }

    // The next swipe of the card the state is on is allowed again once the state has moved on
    remember(state.currentIndex) { gate.reset() }

    fun handleSwiped(index: Int, direction: SwipeDirection) {
        // A swipe the state cannot take (stale or a duplicate) leaves the stack wrong, so it is put back
        if (gate.accept(index, latest.currentIndex)) onIntent(SwipeIntent.Swipe(direction)) else syncTick++
    }

    // Buttons and accessibility actions: animate the card away like a gesture, then report it like one. The
    // library only reports gestures, so this is the one place a programmatic swipe is reported.
    fun swipeBy(direction: SwipeDirection) {
        if (busy || latest.isFinished || stack.isAnimationRunning) return
        val index = stack.visibleItemIndex
        busy = true
        scope.launch {
            try {
                stack.animateToNext(direction)
                handleSwiped(index, direction)
            } catch (e: CancellationException) {
                throw e
            } finally {
                busy = false
            }
        }
    }

    fun reportImageSize(item: DeckPhoto, decoded: IntSize) {
        val ratio = decoded.aspectRatioOrNull() ?: return
        val known = decodedAspects[item.id]
        if (known == null || abs(known - ratio) / known > ASPECT_TOLERANCE) decodedAspects[item.id] = ratio
    }

    // The library starts the card behind at scale 0 and only sets its resting scale on the first move
    LaunchedEffect(Unit) {
        if (latest.deck.isNotEmpty()) stack.snapTo(stack.visibleItemIndex)
    }

    // The stack follows the state: nothing after a gesture (it has advanced itself), one card back for an undo,
    // a jump for anything else. Only a change of the state starts this, never a change of the stack, which
    // passes through a moment where the stack is ahead of the state.
    LaunchedEffect(state.currentIndex, state.deck.size, syncTick) {
        val deck = latest.deck
        if (deck.isNotEmpty()) {
            val target = latest.currentIndex.coerceIn(0, deck.lastIndex)
            when (planMove(stack.visibleItemIndex, target)) {
                StackMove.None -> Unit
                StackMove.Back -> stack.animateToBack(directionOf(shown.deck.getOrNull(target)?.decision))
                StackMove.Snap -> stack.snapTo(target)
            }
        }
        shown = latest
    }

    val photoDescription = state.currentPhoto?.takenAtText
        ?.let { stringResource(R.string.swipe_card_description, it) }
        ?: stringResource(R.string.swipe_card_description_undated)
    val deleteLabel = stringResource(R.string.swipe_action_delete)
    val keepLabel = stringResource(R.string.swipe_action_keep)

    BoxWithConstraints(
        modifier = modifier
            .testTag(SwipeTags.CARD)
            .semantics {
                contentDescription = photoDescription
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) { swipeBy(SwipeDirection.Left); true },
                    CustomAccessibilityAction(keepLabel) { swipeBy(SwipeDirection.Right); true },
                )
            },
    ) {
        val areaBottom = SwipeLayout.cardAreaBottom(state.buttonsMode)
        val areaWidth = (maxWidth - SwipeLayout.CardInset * 2).coerceAtLeast(0.dp)
        val areaHeight = (maxHeight - SwipeLayout.CardAreaTop - areaBottom).coerceAtLeast(0.dp)

        fun cardSizeOf(item: DeckPhoto): CardSize =
            fitInside(areaWidth.value, areaHeight.value, item.aspectRatio ?: decodedAspects[item.id] ?: PROVISIONAL_ASPECT_RATIO)

        PreloadPhotos(
            photos = (1..PRELOAD_AHEAD)
                .mapNotNull { state.deck.getOrNull(state.currentIndex + it) }
                .map { item ->
                    val size = cardSizeOf(item)
                    with(density) { PreloadPhoto(item.photo.uri, IntSize(size.width.dp.roundToPx(), size.height.dp.roundToPx())) }
                },
        )

        // The library measures every swipe distance and threshold against this size, in dp, and it is 0 until the
        // app provides one: with 0 the scale and the rotation of the card come out as NaN and the card disappears
        val window = ComposeWindow(width = areaWidth.value.toInt(), height = areaHeight.value.toInt())
        CompositionLocalProvider(LocalComposeWindow provides window) {
            Box(
                Modifier.padding(
                    start = SwipeLayout.CardInset,
                    end = SwipeLayout.CardInset,
                    top = SwipeLayout.CardAreaTop,
                    bottom = areaBottom,
                ),
            ) {
                LazyCardStack(
                    modifier = Modifier.fillMaxSize(),
                    directions = setOf(SwipeDirection.Left, SwipeDirection.Right),
                    state = stack,
                    onSwipedItem = { index, direction -> handleSwiped(index, direction) },
                ) {
                    items(count = state.deck.size, key = { state.deck[it].id }) { index ->
                        val item = state.deck[index]
                        val size = cardSizeOf(item)
                        SwipeCard(
                            item = item,
                            isTop = index == stack.visibleItemIndex,
                            stack = stack,
                            cardWidth = size.width.dp,
                            cardHeight = size.height.dp,
                            onImageSize = { decoded -> if (item.aspectRatio == null) reportImageSize(item, decoded) },
                        )
                    }
                }
            }
            val current = state.currentPhoto
            if (state.buttonsMode && current != null) {
                val size = cardSizeOf(current)
                DecisionButtons(
                    cardWidth = size.width.dp,
                    onDelete = { swipeBy(SwipeDirection.Left) },
                    onKeep = { swipeBy(SwipeDirection.Right) },
                    // The circles are centred on the bottom edge of the card, wherever that is
                    modifier = Modifier.offset(
                        x = SwipeLayout.CardInset + (areaWidth - size.width.dp) / 2,
                        y = SwipeLayout.CardAreaTop + (areaHeight + size.height.dp) / 2 - SwipeLayout.DecisionButton / 2,
                    ),
                )
            }
        }
    }
}
