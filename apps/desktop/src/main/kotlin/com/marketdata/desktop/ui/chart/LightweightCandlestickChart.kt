@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.marketdata.desktop.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.theme.DeskColors
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Composable
internal fun LightweightCandlestickChart(
    chartKey: String,
    candles: List<ChartCandle>,
    height: Dp
) {
    if (candles.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(height).background(DeskColors.elevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "실시간 캔들을 불러오는 중입니다.",
                color = DeskColors.muted,
                style = MaterialTheme.typography.body2
            )
        }
        return
    }

    val panel = remember { LightweightChartPanel() }
    DisposableEffect(panel) { onDispose(panel::dispose) }
    Box(Modifier.fillMaxWidth().height(height).background(DeskColors.elevated)) {
        SwingPanel(
            factory = { panel },
            update = { it.update(chartKey, candles) },
            modifier = Modifier.fillMaxSize(),
            background = Color(0xFF121414)
        )
    }
}

internal class LightweightChartPanel : JFXPanel() {
    @Volatile private var ready = false
    @Volatile private var disposed = false
    @Volatile private var pendingKey = ""
    @Volatile private var pendingData = "[]"
    private var webView: WebView? = null
    private val readyFuture = CompletableFuture<Boolean>()

    init {
        background = java.awt.Color(18, 20, 20)
        Platform.setImplicitExit(false)
        Platform.runLater {
            if (disposed) return@runLater
            val view = WebView()
            webView = view
            scene = Scene(view, javafx.scene.paint.Color.rgb(18, 20, 20))
            view.engine.loadWorker.stateProperty().addListener { _, _, state ->
                if (state == Worker.State.SUCCEEDED) {
                    ready = runCatching {
                        view.engine.executeScript("typeof window.marketChartUpdate === 'function'") == true
                    }.getOrDefault(false)
                    readyFuture.complete(ready)
                    if (ready) pushPending()
                } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                    readyFuture.complete(false)
                }
            }
            view.engine.loadContent(CHART_HTML)
        }
    }

    fun update(chartKey: String, candles: List<ChartCandle>) {
        pendingKey = jsonString(chartKey)
        pendingData = candles.toChartJson()
        if (ready) Platform.runLater(::pushPending)
    }

    fun dispose() {
        disposed = true
        Platform.runLater {
            ready = false
            webView?.engine?.load(null)
            webView = null
            scene = null
        }
    }

    fun awaitReady(timeoutSeconds: Long): Boolean =
        readyFuture.get(timeoutSeconds, TimeUnit.SECONDS)

    fun dataPointCount(timeoutSeconds: Long): Int {
        val result = CompletableFuture<Int>()
        Platform.runLater {
            result.complete((webView?.engine?.executeScript("window.marketChartDataLength || 0") as? Number)?.toInt() ?: 0)
        }
        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    fun lastCandleClosed(timeoutSeconds: Long): Boolean {
        val result = CompletableFuture<Boolean>()
        Platform.runLater {
            result.complete(webView?.engine?.executeScript("window.marketChartLastClosed === true") == true)
        }
        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    private fun pushPending() {
        if (disposed || !ready || pendingKey.isBlank()) return
        webView?.engine?.executeScript("window.marketChartUpdate($pendingKey,$pendingData)")
    }
}

private fun List<ChartCandle>.toChartJson() = joinToString(prefix = "[", postfix = "]") { candle ->
    val liveColor = if (candle.closed) "" else ",\"color\":\"#9BA49E\",\"borderColor\":\"#9BA49E\",\"wickColor\":\"#9BA49E\""
    "{\"time\":${candle.time},\"open\":${candle.open},\"high\":${candle.high}," +
        "\"low\":${candle.low},\"close\":${candle.close},\"closed\":${candle.closed}$liveColor}"
}

private fun jsonString(value: String) = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            else -> append(character)
        }
    }
    append('"')
}

private const val CHART_HTML = """
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    html,body,#chart{width:100%;height:100%;margin:0;background:#121414;overflow:hidden}
    #chart{height:calc(100% - 18px)}
    #credit{height:18px;color:#737b76;font:10px sans-serif;text-align:right;padding-right:8px;box-sizing:border-box}
    #error{display:none;position:absolute;inset:0;align-items:center;justify-content:center;color:#9ba49e;font:13px sans-serif}
    a{color:#8ae6ad;text-decoration:none}
  </style>
</head>
<body>
  <div id="chart"></div>
  <div id="credit">Charts by <a href="https://www.tradingview.com/">TradingView</a></div>
  <div id="error">차트 라이브러리를 불러오지 못했습니다.</div>
  <script src="https://unpkg.com/lightweight-charts@5.2.1/dist/lightweight-charts.standalone.production.js"
          integrity="sha384-KkYqTZlM13Zya6fVUF3IGCBOQ1ehFeJVVgxaVvD/0MebineF9EI8Jkd3NnWNChqN" crossorigin="anonymous"
          onerror="document.getElementById('error').style.display='flex'"></script>
  <script>
    const chart = LightweightCharts.createChart(document.getElementById('chart'), {
      autoSize: true,
      layout: { background: { color: '#121414' }, textColor: '#9ba49e' },
      grid: { vertLines: { color: '#202422' }, horzLines: { color: '#202422' } },
      rightPriceScale: { borderColor: '#2c302e' },
      timeScale: { borderColor: '#2c302e', timeVisible: true, secondsVisible: false },
      crosshair: { mode: LightweightCharts.CrosshairMode.Normal }
    });
    const series = chart.addSeries(LightweightCharts.CandlestickSeries, {
      upColor: '#8ae6ad', downColor: '#f2aa98', borderVisible: false,
      wickUpColor: '#8ae6ad', wickDownColor: '#f2aa98'
    });
    let currentKey = '';
    let currentLength = 0;
    window.marketChartDataLength = 0;
    window.marketChartLastClosed = false;
    window.marketChartUpdate = (key, data) => {
      if (!data.length) return;
      if (key !== currentKey || data.length < currentLength || data.length > currentLength + 1) {
        currentKey = key;
        currentLength = data.length;
        const minimum = Math.min(...data.map(value => value.low));
        const precision = minimum < 0.01 ? 8 : minimum < 1 ? 6 : minimum < 100 ? 4 : 2;
        series.applyOptions({ priceFormat: { type: 'price', precision, minMove: Math.pow(10, -precision) } });
        series.setData(data);
        chart.timeScale().fitContent();
      } else {
        if (data.length === currentLength + 1 && currentLength > 0) {
          series.update(data[data.length - 2]);
        }
        currentLength = data.length;
        series.update(data[data.length - 1]);
      }
      window.marketChartDataLength = data.length;
      window.marketChartLastClosed = data[data.length - 1].closed === true;
    };
  </script>
</body>
</html>
"""
