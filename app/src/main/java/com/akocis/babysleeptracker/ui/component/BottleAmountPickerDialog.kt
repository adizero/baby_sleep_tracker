package com.akocis.babysleeptracker.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val ML_AMOUNTS = (5..300 step 5).toList()
private val OZ_AMOUNTS_X10 = (5..100 step 5).toList() // 0.5 to 10.0 oz in 0.5 steps

private fun mlToOzX10(ml: Int): Int = ((ml / 29.5735) * 10).roundToInt().coerceIn(5, 100)
private fun ozX10ToMl(ozX10: Int): Int = ((ozX10 / 10.0) * 29.5735).roundToInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleAmountPickerDialog(
    title: String = "Bottle Amount",
    initialAmount: Int,
    useOz: Boolean = false,
    onConfirm: (ml: Int, useOz: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var isOz by remember { mutableStateOf(useOz) }

    val snappedMl = ML_AMOUNTS.minBy { kotlin.math.abs(it - initialAmount) }
    val snappedOzX10 = OZ_AMOUNTS_X10.minBy { kotlin.math.abs(it - mlToOzX10(initialAmount)) }

    var selectedMl by remember { mutableIntStateOf(snappedMl) }
    var selectedOzX10 by remember { mutableIntStateOf(snappedOzX10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to "ml", true to "oz").forEachIndexed { index, (oz, label) ->
                        SegmentedButton(
                            selected = isOz == oz,
                            onClick = {
                                if (isOz != oz) {
                                    if (oz) {
                                        // switching to oz: convert current ml
                                        selectedOzX10 = OZ_AMOUNTS_X10.minBy {
                                            kotlin.math.abs(it - mlToOzX10(selectedMl))
                                        }
                                    } else {
                                        // switching to ml: convert current oz
                                        selectedMl = ML_AMOUNTS.minBy {
                                            kotlin.math.abs(it - ozX10ToMl(selectedOzX10))
                                        }
                                    }
                                    isOz = oz
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                        ) { Text(label) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isOz) {
                    WheelPicker(
                        items = OZ_AMOUNTS_X10,
                        initialValue = selectedOzX10,
                        onValueChanged = { selectedOzX10 = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "oz",
                        formatItem = { "%.1f".format(it / 10.0) }
                    )
                } else {
                    WheelPicker(
                        items = ML_AMOUNTS,
                        initialValue = selectedMl,
                        onValueChanged = { selectedMl = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ml = if (isOz) ozX10ToMl(selectedOzX10) else selectedMl
                onConfirm(ml, isOz)
            }) {
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
