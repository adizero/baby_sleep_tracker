package com.akocis.babysleeptracker.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private val BOTTLE_AMOUNTS = (5..300 step 5).toList()

@Composable
fun BottleAmountPickerDialog(
    title: String = "Bottle Amount",
    initialAmount: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val snapped = BOTTLE_AMOUNTS.minBy { kotlin.math.abs(it - initialAmount) }
    var selectedAmount by remember { mutableIntStateOf(snapped) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            WheelPicker(
                items = BOTTLE_AMOUNTS,
                initialValue = snapped,
                onValueChanged = { selectedAmount = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedAmount) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
