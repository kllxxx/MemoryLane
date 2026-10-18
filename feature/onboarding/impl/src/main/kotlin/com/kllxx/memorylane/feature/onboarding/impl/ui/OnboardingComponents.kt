package com.kllxx.memorylane.feature.onboarding.impl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kllxx.memorylane.core.ui.theme.Spacing
import com.kllxx.memorylane.feature.onboarding.impl.R

internal val BackButtonSize = 40.dp
internal val SlideSurface = Color(0xFFF1F6F2)
internal val SlideBorder = Emerald.copy(alpha = 0.3f)

@Composable
internal fun OnboardingTopBar(
    showBack: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(BackButtonSize)
                    .clip(CircleShape)
                    .background(SlideSurface)
                    .border(BorderStroke(1.dp, SlideBorder), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back),
                    tint = Licorice.copy(alpha = 0.7f),
                )
            }
        } else {
            // Keeps the skip button in place on the first slide
            Spacer(Modifier.size(BackButtonSize))
        }
        TextButton(onClick = onSkip) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                color = Licorice.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
internal fun SlideText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = Licorice,
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
        )
        Text(
            text = subtitle,
            color = Licorice.copy(alpha = 0.6f),
            fontFamily = NunitoFontFamily,
            fontSize = 15.sp,
            lineHeight = 24.sp,
        )
    }
}

/** A check or cross line from the slides, [positive] picks green check over red cross. */
@Composable
internal fun InfoRow(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    val tint = if (positive) DarkSpringGreen else RedCrayola
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (positive) SlideSurface else RedCrayola.copy(alpha = 0.06f))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.2f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .border(BorderStroke(1.5.dp, tint), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (positive) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            color = Licorice,
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

/** Page background shared by all slides. */
@Composable
internal fun OnboardingSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = BabyPowder, content = content)
}
