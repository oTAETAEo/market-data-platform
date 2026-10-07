package com.marketdata.desktop.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.components.DropdownControl
import com.marketdata.desktop.ui.components.ToolButton
import com.marketdata.desktop.ui.theme.DeskColors

@Composable
internal fun AiSettingsDialog(viewModel: WorkspaceViewModel, onDismiss: () -> Unit) {
    var provider by remember { mutableStateOf(viewModel.aiProvider) }
    var quickModel by remember { mutableStateOf(viewModel.quickModel) }
    var deepModel by remember { mutableStateOf(viewModel.deepModel) }
    var apiKey by remember { mutableStateOf(viewModel.apiKey) }
    val keyOptional = provider in setOf("ollama", "openai_compatible", "bedrock")

    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.widthIn(min = 360.dp, max = 520.dp), color = DeskColors.panel,
            shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("AI 에이전트 설정", style = MaterialTheme.typography.h6)
                        Text("현재 앱 세션에만 유지됩니다.", color = DeskColors.muted,
                            style = MaterialTheme.typography.caption)
                    }
                    ToolButton(Icons.Default.Close, "닫기", onClick = onDismiss)
                }
                DropdownControl(provider,
                    listOf("openai", "anthropic", "google", "openrouter", "ollama", "openai_compatible"),
                    "AI 제공자") { provider = it }
                OutlinedTextField(quickModel, { quickModel = it }, label = { Text("빠른 모델") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deepModel, { deepModel = it }, label = { Text("심층 모델") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it },
                    label = { Text(if (keyOptional) "API 키 (선택)" else "API 키") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text("API 키는 파일에 저장하지 않고 Python 분석 프로세스에 한 번만 전달합니다.",
                    color = DeskColors.muted, style = MaterialTheme.typography.caption)
                Button(onClick = {
                    viewModel.updateAiSettings(provider, quickModel, deepModel, apiKey)
                    onDismiss()
                }, enabled = quickModel.isNotBlank() && deepModel.isNotBlank() && (keyOptional || apiKey.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("설정 적용")
                }
            }
        }
    }
}
