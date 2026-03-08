package com.akocis.babysleeptracker.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akocis.babysleeptracker.model.TrackingState
import com.akocis.babysleeptracker.ui.theme.FeedColor
import com.akocis.babysleeptracker.ui.theme.SleepButtonColor
import com.akocis.babysleeptracker.ui.theme.SoftGreen

@Composable
fun StatusBanner(
    trackingState: TrackingState,
    elapsedTime: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (trackingState) {
            is TrackingState.Sleeping -> SleepButtonColor
            is TrackingState.Feeding -> FeedColor
            is TrackingState.Idle -> SoftGreen
        },
        label = "statusColor"
    )

    val statusText = when (trackingState) {
        is TrackingState.Sleeping -> "Sleeping"
        is TrackingState.Feeding -> "Feeding (${trackingState.side.label[0]})"
        is TrackingState.Idle -> "Awake"
    }

    val showTimer = elapsedTime.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            if (showTimer) {
                Text(
                    text = elapsedTime,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
