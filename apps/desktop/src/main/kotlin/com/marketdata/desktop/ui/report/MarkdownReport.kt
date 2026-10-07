package com.marketdata.desktop.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material.MaterialRichText

@Composable
internal fun MarkdownReport(markdown: String, modifier: Modifier = Modifier) {
    MaterialRichText(modifier.fillMaxWidth()) {
        Markdown(markdown)
    }
}
