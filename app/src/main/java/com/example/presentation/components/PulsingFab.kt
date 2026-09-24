package com.example.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NoteDimens

@Composable
fun PulsingFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_scale"
    )

    FloatingActionButton(
        onClick = onClick,
        shape = NoteDimens.shapeLarge,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = NoteDimens.elevationMedium),
        modifier = modifier
            .scale(scale)
            .size(56.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Создать заметку",
            modifier = Modifier.size(28.dp)
        )
    }
}
