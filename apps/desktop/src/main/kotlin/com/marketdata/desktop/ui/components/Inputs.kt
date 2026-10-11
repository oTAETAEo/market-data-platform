@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.marketdata.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.ui.theme.DeskColors
import java.awt.Component
import kotlin.math.roundToInt

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(modifier.height(40.dp).background(DeskColors.elevated, CircleShape).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = DeskColors.muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value, onChange, singleLine = true,
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            textStyle = MaterialTheme.typography.body2.copy(color = DeskColors.text),
            cursorBrush = SolidColor(DeskColors.green),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(placeholder, color = DeskColors.muted, style = MaterialTheme.typography.body2); inner() }
            }
        )
        if (value.isNotEmpty()) ToolButton(Icons.Default.Close, "검색 지우기", size = 28.dp) { onChange("") }
    }
}

@Composable
internal fun DropdownControl(
    value: String,
    values: List<String>,
    label: String,
    nativePopupOwner: Component? = null,
    onChange: (String) -> Unit
) {
    val density = LocalDensity.current.density
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val menu = remember { NativeDropdownMenu() }
    DisposableEffect(menu) { onDispose { menu.isVisible = false } }
    Box {
        OutlinedButton(onClick = {
            if (nativePopupOwner == null) {
                open = !open
            } else {
                menu.toggle(nativePopupOwner, (anchor.left / density).roundToInt(), (anchor.bottom / density).roundToInt()) {
                    menu.setOptions(value, values, onChange)
                }
            }
        }, border = BorderStroke(1.dp, DeskColors.line),
            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = DeskColors.elevated, contentColor = DeskColors.text),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }
                .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                    if (nativePopupOwner != null && event.buttons.isPrimaryPressed) {
                        menu.captureButtonPress()
                        menu.toggle(nativePopupOwner, (anchor.left / density).roundToInt(), (anchor.bottom / density).roundToInt()) {
                            menu.setOptions(value, values, onChange)
                        }
                        // Handle native popup toggling on press, without a second action on release.
                        event.changes.forEach { it.consume() }
                    }
                }
                .semantics { contentDescription = label }) {
            Text(value, style = MaterialTheme.typography.subtitle2)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = DeskColors.muted, modifier = Modifier.size(16.dp))
        }
        if (nativePopupOwner == null) DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { option ->
                DropdownMenuItem(onClick = { open = false; onChange(option) }) {
                    Text(option, color = if (value == option) DeskColors.green else DeskColors.text)
                }
            }
        }
    }
}
