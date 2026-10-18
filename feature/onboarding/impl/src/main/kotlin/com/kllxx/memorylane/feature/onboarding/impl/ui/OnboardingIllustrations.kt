package com.kllxx.memorylane.feature.onboarding.impl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.theme.BabyPowder
import com.kllxx.memorylane.core.ui.theme.DarkSpringGreen
import com.kllxx.memorylane.core.ui.theme.Emerald
import com.kllxx.memorylane.core.ui.theme.Licorice
import com.kllxx.memorylane.core.ui.theme.NunitoFontFamily
import com.kllxx.memorylane.core.ui.theme.RedCrayola
import com.kllxx.memorylane.feature.onboarding.impl.R

private val CardShape = RoundedCornerShape(22.dp)

@Composable
private fun PhotoCard(caption: String, modifier: Modifier = Modifier, elevated: Boolean = true) {
    Column(
        modifier = modifier
            .width(170.dp)
            .height(190.dp)
            .shadow(if (elevated) 10.dp else 0.dp, CardShape, ambientColor = Emerald, spotColor = Emerald)
            .clip(CardShape)
            .background(BabyPowder),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Emerald.copy(alpha = 0.55f), Emerald.copy(alpha = 0.85f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = BabyPowder.copy(alpha = 0.55f),
                modifier = Modifier.size(56.dp),
            )
        }
        Text(
            text = caption,
            color = Licorice.copy(alpha = 0.7f),
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun StackedCardsIllustration(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 260.dp, height = 240.dp), contentAlignment = Alignment.Center) {
        PhotoCard(
            caption = "",
            elevated = false,
            modifier = Modifier
                .offset(x = (-26).dp, y = 10.dp)
                .rotate(-9f)
                .alpha(0.5f),
        )
        PhotoCard(
            caption = "",
            elevated = false,
            modifier = Modifier
                .offset(x = 18.dp, y = 22.dp)
                .rotate(7f)
                .alpha(0.55f),
        )
        Box {
            PhotoCard(caption = stringResource(R.string.onboarding_slide1_caption))
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Emerald),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = BabyPowder, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SwipeHint(label: String, arrow: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f))
                .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = arrow, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    }
}

@Composable
internal fun SwipeCardIllustration(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SwipeHint(stringResource(R.string.onboarding_slide2_delete), "←", RedCrayola)
        PhotoCard(caption = stringResource(R.string.onboarding_slide2_caption), modifier = Modifier.size(width = 150.dp, height = 170.dp))
        SwipeHint(stringResource(R.string.onboarding_slide2_keep), "→", DarkSpringGreen)
    }
}

@Composable
internal fun LockIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(104.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(SlideSurface)
            .border(BorderStroke(2.dp, SlideBorder), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = DarkSpringGreen, modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun NotificationCard(
    app: String,
    text: String,
    time: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) SlideSurface else BabyPowder)
            .border(BorderStroke(1.dp, SlideBorder), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (highlighted) DarkSpringGreen else Emerald.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = BabyPowder, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = app, color = Licorice, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text(text = text, color = Licorice.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Text(text = time, color = Licorice.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
internal fun NotificationCardsIllustration(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NotificationCard(
            app = stringResource(R.string.onboarding_notification_app),
            text = stringResource(R.string.onboarding_notification_unsorted),
            time = stringResource(R.string.onboarding_notification_unsorted_time),
            modifier = Modifier.alpha(0.4f),
        )
        NotificationCard(
            app = stringResource(R.string.onboarding_notification_app),
            text = stringResource(R.string.onboarding_notification_waiting),
            time = stringResource(R.string.onboarding_notification_waiting_time),
            modifier = Modifier.alpha(0.7f),
        )
        NotificationCard(
            app = stringResource(R.string.onboarding_notification_app_now),
            text = stringResource(R.string.onboarding_notification_streak),
            time = stringResource(R.string.onboarding_notification_streak_time),
            highlighted = true,
        )
    }
}
