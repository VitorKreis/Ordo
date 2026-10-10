package com.vitorkrichter.ordo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.vitorkrichter.ordo.core.designsystem.theme.AccentAmber
import com.vitorkrichter.ordo.core.designsystem.theme.AccentBlue
import com.vitorkrichter.ordo.core.designsystem.theme.AccentGreen
import com.vitorkrichter.ordo.core.designsystem.theme.AccentRed
import com.vitorkrichter.ordo.core.designsystem.theme.AccentTeal
import com.vitorkrichter.ordo.core.designsystem.theme.BadgeInfoBg
import com.vitorkrichter.ordo.core.designsystem.theme.BadgeLateBg
import com.vitorkrichter.ordo.core.designsystem.theme.BadgeOkBg
import com.vitorkrichter.ordo.core.designsystem.theme.BadgePendBg
import com.vitorkrichter.ordo.core.designsystem.theme.BgCard
import com.vitorkrichter.ordo.core.designsystem.theme.BorderSubtle
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextMuted
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary

@Composable
fun OrdoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, BorderSubtle),
        colors = CardDefaults.outlinedCardColors(containerColor = BgCard),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}

@Composable
fun OrdoActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
) {
    if (secondary) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextPrimary,
                disabledContentColor = TextMuted,
            ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, modifier = Modifier.padding(start = Spacing.sm))
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                contentColor = TextPrimary,
                disabledContainerColor = AccentBlue.copy(alpha = 0.35f),
                disabledContentColor = TextSecondary,
            ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

@Composable
fun OrdoStatusBadge(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val (bg, textColor) = when (tone) {
        StatusTone.Good -> BadgeOkBg to AccentGreen
        StatusTone.Pending -> BadgePendBg to AccentAmber
        StatusTone.Info -> BadgeInfoBg to AccentTeal
        StatusTone.Bad -> BadgeLateBg to AccentRed
        StatusTone.Muted -> BorderSubtle to TextSecondary
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = bg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

@Composable
fun OrdoEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OrdoIconBubble(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        action?.invoke()
    }
}

@Composable
fun OrdoIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AccentTeal,
) {
    Surface(
        modifier = modifier.size(52.dp),
        shape = CircleShape,
        color = AccentBlue.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.28f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun OrdoInlineIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AccentTeal,
) {
    Surface(
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = tint.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun OrdoMetricStrip(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    thirdLabel: String,
    thirdValue: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MetricCell(firstValue, firstLabel, AccentBlue, Modifier.weight(1f))
        MetricCell(secondValue, secondLabel, AccentGreen, Modifier.weight(1f))
        MetricCell(thirdValue, thirdLabel, AccentAmber, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = accentColor)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

enum class StatusTone {
    Good,
    Pending,
    Info,
    Bad,
    Muted,
}
