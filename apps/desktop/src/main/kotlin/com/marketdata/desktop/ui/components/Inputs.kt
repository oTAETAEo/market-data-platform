@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.marketdata.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String,
                         modifier: Modifier = Modifier, inputModifier: Modifier = Modifier,
                         onFocusChange: (Boolean) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var restoreFocus by remember { mutableStateOf(false) }
    LaunchedEffect(restoreFocus) {
        if (restoreFocus) {
            focusRequester.requestFocus()
            restoreFocus = false
        }
    }
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.height(40.dp)
        .background(if (MaterialTheme.colors.isLight) DeskColors.panel else DeskColors.elevated, shape)
        .border(1.dp, if (focused) DeskColors.green else DeskColors.inputLine, shape)
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = DeskColors.muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value, onChange, singleLine = true,
            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focusRequester).then(inputModifier)
                .onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) }
                .semantics { contentDescription = placeholder },
            textStyle = MaterialTheme.typography.body2.copy(color = DeskColors.text),
            cursorBrush = SolidColor(DeskColors.green),
            decorationBox = { inner ->
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = DeskColors.muted, style = MaterialTheme.typography.body2)
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) ToolButton(Icons.Default.Close, "검색 지우기", size = 28.dp) {
            onChange("")
            // Restore focus after the clear button and suggestions leave composition.
            restoreFocus = true
        }
    }
}

@Composable
internal fun DropdownControl(
    value: String,
    values: List<String>,
    label: String,
    onChange: (String) -> Unit
) {
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableStateOf(0.dp) }
    val positionProvider = rememberDropdownPositionProvider()
    Box {
        OutlinedButton(onClick = { open = !open },
            border = BorderStroke(1.dp, if (open) DeskColors.green.copy(alpha = 0.5f) else DeskColors.line),
            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = DeskColors.elevated, contentColor = DeskColors.text),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            modifier = Modifier.onSizeChanged { buttonWidth = with(density) { it.width.toDp() } }
                .semantics { contentDescription = label }) {
            Text(value, style = MaterialTheme.typography.subtitle2)
            Spacer(Modifier.width(12.dp))
            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, tint = if (open) DeskColors.green else DeskColors.muted, modifier = Modifier.size(16.dp))
        }
        // The chart is a Compose image, so menus no longer need separate native windows.
        if (open) Popup(popupPositionProvider = positionProvider,
            properties = PopupProperties(focusable = true), onDismissRequest = { open = false }) {
            val focusManager = LocalFocusManager.current
            val initialFocus = remember { FocusRequester() }
            Surface(color = DeskColors.elevated, shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DeskColors.line)) {
                Column(Modifier.width(maxOf(156.dp, buttonWidth.coerceAtMost(280.dp)))
                    .heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(6.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false
                        else when (event.key) {
                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Next)
                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Previous)
                            else -> false
                        }
                    }) {
                    values.forEachIndexed { index, option ->
                        val selected = value == option
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        val focused by interaction.collectIsFocusedAsState()
                        val firstFocus = selected || (value !in values && index == 0)
                        Row(Modifier.fillMaxWidth().height(38.dp)
                            .background(when {
                                hovered || focused -> DeskColors.hover
                                selected -> DeskColors.green.copy(alpha = 0.08f)
                                else -> DeskColors.elevated
                            }, RoundedCornerShape(4.dp))
                            .then(if (firstFocus) Modifier.focusRequester(initialFocus) else Modifier)
                            .hoverable(interaction)
                            .selectable(selected, interactionSource = interaction, indication = null,
                                role = Role.RadioButton, onClick = { open = false; onChange(option) })
                            .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option, color = if (selected) DeskColors.green else DeskColors.text,
                                style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(16.dp))
                            if (selected) Icon(Icons.Default.Check, null, tint = DeskColors.green, modifier = Modifier.size(16.dp))
                            else Spacer(Modifier.size(16.dp))
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { if (values.isNotEmpty()) initialFocus.requestFocus() }
        }
    }
}

@Composable
internal fun rememberDropdownPositionProvider(): PopupPositionProvider {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    return remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                                           layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val left = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
                    else anchorBounds.right - popupContentSize.width
                val below = anchorBounds.bottom + gap
                val top = if (below + popupContentSize.height <= windowSize.height - gap) below
                    else anchorBounds.top - popupContentSize.height - gap
                return IntOffset(
                    left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                    top.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
                )
            }
        }
    }
}
