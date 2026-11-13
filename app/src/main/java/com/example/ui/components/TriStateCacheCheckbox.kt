package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AntiqueGold

/**
 * 4-state cache indicator checkbox according to user specification:
 * 1. isChecked = true && isCached = true -> Normal / Gold check
 * 2. isChecked = true && isCached = false -> 赤いチェック (Red checkmark: downloading / caching in progress)
 * 3. isChecked = false && isCached = true -> チェックボックスを赤く (Red box outline: cache exists, waiting delete)
 * 4. isChecked = false && isCached = false -> Normal unchecked box
 */
@Composable
fun CacheTreeCheckbox(
    isChecked: Boolean,
    isCached: Boolean,
    isPartial: Boolean = false,
    isBusy: Boolean = false,
    onToggle: () -> Unit,
    size: Dp = 22.dp,
    testTag: String = "cache_checkbox",
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isPartial -> PartialAmberColor
            isChecked && !isCached -> RedAlertColor // 赤いチェック (キャッシュ未完了)
            !isChecked && isCached -> RedAlertColor // 赤いチェックボックス (キャッシュ削除待ち)
            isChecked && isCached -> AntiqueGold
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        label = "borderColor"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isPartial -> PartialAmberColor.copy(alpha = 0.18f)
            isChecked && isCached -> AntiqueGold
            isChecked && !isCached -> RedAlertBg // 赤いチェック背景
            !isChecked && isCached -> RedAlertBg // チェック外れキャッシュ残り赤背景
            else -> Color.Transparent
        },
        label = "bgColor"
    )

    val checkColor = when {
        isPartial -> PartialAmberColor
        isChecked && !isCached -> RedAlertColor // 赤いチェック
        isChecked && isCached -> Color.Black
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(5.dp))
            .clickable { onToggle() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(size * 0.7f),
                strokeWidth = 2.dp,
                color = if (isPartial) PartialAmberColor else if (isChecked) RedAlertColor else AntiqueGold
            )
        } else if (isPartial) {
            // 中間状態（一部キャッシュ済）の水平インジケータ
            Box(
                modifier = Modifier
                    .size(width = size * 0.55f, height = 2.5.dp)
                    .background(PartialAmberColor, RoundedCornerShape(1.dp))
            )
        } else if (isChecked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = if (isCached) "キャッシュ済み" else "キャッシュ中(未完了)",
                tint = checkColor,
                modifier = Modifier.size(size * 0.85f)
            )
        }
    }
}
