package com.example.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    tooltip: String,
    modifier: Modifier = Modifier,
    contentDescription: String = tooltip,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltip)
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltip)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content
    )
}
