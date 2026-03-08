package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akocis.babysleeptracker.model.NoiseType

data class NoiseSettings(
    val noiseType: NoiseType,
    val durationMs: Long,
    val fadeInSeconds: Float,
    val fadeOutSeconds: Float,
    val volume: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteNoiseDialog(
    initialNoiseType: NoiseType = NoiseType.WHITE,
    initialVolume: Float = 0.5f,
    initialFadeIn: Int = 0,
    initialFadeOut: Int = 0,
    onStart: (NoiseSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var noiseType by remember { mutableStateOf(initialNoiseType) }
    var durationIndex by remember { mutableIntStateOf(0) }
    var fadeInIndex by remember { mutableIntStateOf(initialFadeIn) }
    var fadeOutIndex by remember { mutableIntStateOf(initialFadeOut) }
    var volume by remember { mutableFloatStateOf(initialVolume) }

    val durationOptions = listOf("None" to 0L, "15m" to 900_000L, "30m" to 1_800_000L, "1h" to 3_600_000L, "2h" to 7_200_000L)
    val fadeOptions = listOf("Off" to 0f, "5s" to 5f, "15s" to 15f, "30s" to 30f, "1m" to 60f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("White Noise") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Noise type
                Text("Type", style = MaterialTheme.typography.labelLarge)
                NoiseType.entries.chunked(4).forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowTypes.forEach { type ->
                            FilterChip(
                                selected = noiseType == type,
                                onClick = { noiseType = type },
                                label = { Text(type.label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                modifier = Modifier.weight(1f),
                                leadingIcon = null
                            )
                        }
                        // Pad with spacers if row has fewer than 4
                        repeat(4 - rowTypes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Duration
                Text("Duration", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    durationOptions.forEachIndexed { index, (label, _) ->
                        SegmentedButton(
                            selected = durationIndex == index,
                            onClick = { durationIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index, durationOptions.size)
                        ) {
                            Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Fade-in
                Text("Fade-in", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    fadeOptions.forEachIndexed { index, (label, _) ->
                        SegmentedButton(
                            selected = fadeInIndex == index,
                            onClick = { fadeInIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index, fadeOptions.size)
                        ) {
                            Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Fade-out
                Text("Fade-out", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    fadeOptions.forEachIndexed { index, (label, _) ->
                        SegmentedButton(
                            selected = fadeOutIndex == index,
                            onClick = { fadeOutIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index, fadeOptions.size)
                        ) {
                            Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Volume
                Text("Volume", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(
                        NoiseSettings(
                            noiseType = noiseType,
                            durationMs = durationOptions[durationIndex].second,
                            fadeInSeconds = fadeOptions[fadeInIndex].second,
                            fadeOutSeconds = fadeOptions[fadeOutIndex].second,
                            volume = volume
                        )
                    )
                }
            ) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
