@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.marketdata.desktop.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.unit.Dp
import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.theme.DeskColors
import javafx.application.Platform
import javafx.animation.AnimationTimer
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.SnapshotParameters
import javafx.scene.image.WritableImage
import javafx.scene.transform.Scale
import javafx.scene.web.WebView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import kotlin.math.ceil
import java.awt.Desktop
import java.net.URI

@Composable
internal fun LightweightCandlestickChart(
    chartKey: String,
    candles: List<ChartCandle>,
    height: Dp,
    renderer: LightweightChartPanel? = null
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

    val panel = renderer ?: remember { LightweightChartPanel() }
    val density = LocalDensity.current.density
    val frame = panel.frame.takeIf { panel.frameKey == chartKey }
    DisposableEffect(panel) { onDispose(panel::dispose) }
    LaunchedEffect(panel, chartKey, candles) { panel.update(chartKey, candles) }
    Box(Modifier.fillMaxWidth().height(height).background(DeskColors.elevated)
        .onSizeChanged { panel.setViewport(it.width / density.toDouble(), it.height / density.toDouble(), density.toDouble()) }
        .onPointerEvent(PointerEventType.Enter) { event ->
            val point = event.changes.firstOrNull()?.position ?: return@onPointerEvent
            panel.pointer("mouseenter", point.x / density.toDouble(), point.y / density.toDouble(), 0)
        }
        .onPointerEvent(PointerEventType.Exit) { panel.pointer("mouseleave", 0.0, 0.0, 0) }
        .onPointerEvent(PointerEventType.Move) { event ->
            val point = event.changes.firstOrNull()?.position ?: return@onPointerEvent
            val buttons = (if (event.buttons.isPrimaryPressed) 1 else 0) or
                (if (event.buttons.isSecondaryPressed) 2 else 0) or (if (event.buttons.isTertiaryPressed) 4 else 0)
            panel.pointer("mousemove", point.x / density.toDouble(), point.y / density.toDouble(), buttons)
            if (buttons != 0) event.changes.forEach { it.consume() }
        }
        .onPointerEvent(PointerEventType.Press) { event ->
            val point = event.changes.firstOrNull()?.position ?: return@onPointerEvent
            val buttons = (if (event.buttons.isPrimaryPressed) 1 else 0) or
                (if (event.buttons.isSecondaryPressed) 2 else 0) or (if (event.buttons.isTertiaryPressed) 4 else 0)
            panel.pointer("mousedown", point.x / density.toDouble(), point.y / density.toDouble(), buttons)
            event.changes.forEach { it.consume() }
        }
        .onPointerEvent(PointerEventType.Release) { event ->
            val point = event.changes.firstOrNull()?.position ?: return@onPointerEvent
            panel.pointer("mouseup", point.x / density.toDouble(), point.y / density.toDouble(), 0)
        }
        .onPointerEvent(PointerEventType.Scroll) { event ->
            val change = event.changes.firstOrNull() ?: return@onPointerEvent
            if (change.position.y / density >= height.value - 18) return@onPointerEvent
            panel.wheel(change.position.x / density.toDouble(), change.position.y / density.toDouble(),
                change.scrollDelta.y.toDouble() * 40)
            change.consume()
        }, contentAlignment = Alignment.Center) {
        if (frame != null) {
            Image(frame, contentDescription = "실시간 캔들 차트", modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds)
        } else {
            Text("실시간 캔들을 불러오는 중입니다.", color = DeskColors.muted,
                style = MaterialTheme.typography.body2)
        }
    }
}

// The Swing host initializes JavaFX offscreen; only immutable frames enter the Compose scene.
internal class LightweightChartPanel : JFXPanel() {
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var frameKey by mutableStateOf("")
        private set
    @Volatile private var ready = false
    @Volatile private var disposed = false
    @Volatile private var pendingKey = ""
    @Volatile private var pendingData = "[]"
    private var webView: WebView? = null
    private val readyFuture = CompletableFuture<Boolean>()
    private var viewportWidth = 640.0
    private var viewportHeight = 220.0
    private var pixelScale = 1.0
    private var framesRequested = 0
    private var snapshotImage: WritableImage? = null
    private var snapshotBuffer: BufferedImage? = null
    private var frameTimer: AnimationTimer? = null

    init {
        background = java.awt.Color(18, 20, 20)
        setSize(640, 220)
        Platform.setImplicitExit(false)
        Platform.runLater {
            if (disposed) return@runLater
            val view = WebView()
            view.isContextMenuEnabled = false
            webView = view
            scene = Scene(view, viewportWidth, viewportHeight, javafx.scene.paint.Color.rgb(18, 20, 20))
            applyViewport()
            frameTimer = object : AnimationTimer() {
                private var lastFrameAt = 0L
                override fun handle(now: Long) {
                    if (!ready || framesRequested == 0 || now - lastFrameAt < 33_000_000L) return
                    lastFrameAt = now
                    if (publishFrame()) framesRequested--
                }
            }.also { it.start() }
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
        val key = jsonString(chartKey)
        val data = candles.toChartJson()
        Platform.runLater {
            if (disposed || (pendingKey == key && pendingData == data)) return@runLater
            if (pendingKey != key) frame = null
            pendingKey = key
            pendingData = data
            frameKey = chartKey
            if (ready) pushPending()
        }
    }

    fun setViewport(width: Double, height: Double, scale: Double) {
        if (width <= 0 || height <= 0 || scale <= 0) return
        SwingUtilities.invokeLater { if (!disposed) setSize(ceil(width).toInt(), ceil(height).toInt()) }
        Platform.runLater {
            if (disposed || (viewportWidth == width && viewportHeight == height && pixelScale == scale)) return@runLater
            viewportWidth = width
            viewportHeight = height
            pixelScale = scale
            applyViewport()
            framesRequested = 3
        }
    }

    private fun applyViewport() {
        webView?.apply {
            setMinSize(viewportWidth, viewportHeight)
            setPrefSize(viewportWidth, viewportHeight)
            setMaxSize(viewportWidth, viewportHeight)
            resize(viewportWidth, viewportHeight)
            applyCss()
            layout()
        }
    }

    private fun publishFrame(): Boolean {
        val view = webView ?: return false
        val width = ceil(viewportWidth * pixelScale).toInt()
        val height = ceil(viewportHeight * pixelScale).toInt()
        if (snapshotImage?.width != width.toDouble() || snapshotImage?.height != height.toDouble()) {
            snapshotImage = WritableImage(width, height)
            snapshotBuffer = null
        }
        val parameters = SnapshotParameters().apply {
            fill = javafx.scene.paint.Color.rgb(18, 20, 20)
            transform = Scale(pixelScale, pixelScale)
        }
        view.snapshot(parameters, snapshotImage)
        val pixels = snapshotImage!!.pixelReader
        val colors = mutableSetOf<Int>()
        for (y in 0 until height step 16) for (x in 0 until width step 16) colors.add(pixels.getArgb(x, y))
        if (colors.size <= 4) return false
        snapshotBuffer = SwingFXUtils.fromFXImage(snapshotImage, snapshotBuffer)
        frame = snapshotBuffer!!.toComposeImageBitmap()
        return true
    }

    fun pointer(type: String, x: Double, y: Double, buttons: Int) {
        if (type !in setOf("mouseenter", "mouseleave", "mousedown", "mouseup", "mousemove")) return
        Platform.runLater {
            if (!ready || disposed) return@runLater
            val view = webView ?: return@runLater
            val link = view.engine.executeScript("window.marketChartPointer(${jsonString(type)},$x,$y,$buttons)") as? String
            val uri = link?.let { runCatching { URI(it) }.getOrNull() }
            if (uri?.scheme == "https" && uri.host in setOf("tradingview.com", "www.tradingview.com")) {
                SwingUtilities.invokeLater {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        runCatching { Desktop.getDesktop().browse(uri) }
                    }
                }
            }
            framesRequested = 3
        }
    }

    fun wheel(x: Double, y: Double, delta: Double) {
        Platform.runLater {
            if (!ready || disposed) return@runLater
            val view = webView ?: return@runLater
            view.engine.executeScript("window.marketChartWheel($x,$y,$delta)")
            framesRequested = 3
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        Platform.runLater {
            ready = false
            frameTimer?.stop()
            webView?.engine?.load(null)
            webView = null
            scene = null
        }
    }

    fun captureFrame(): ImageBitmap? = frame

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

    fun lastCandleClose(timeoutSeconds: Long): Double {
        val result = CompletableFuture<Double>()
        Platform.runLater {
            result.complete((webView?.engine?.executeScript(
                "series.dataByIndex(series.data().length - 1).close"
            ) as Number).toDouble())
        }
        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    fun scrollPosition(timeoutSeconds: Long): Double {
        val result = CompletableFuture<Double>()
        Platform.runLater {
            result.complete((webView?.engine?.executeScript("chart.timeScale().scrollPosition()") as Number).toDouble())
        }
        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    fun visibleRangeSpan(timeoutSeconds: Long): Double {
        val result = CompletableFuture<Double>()
        Platform.runLater {
            result.complete((webView?.engine?.executeScript(
                "(() => { const range = chart.timeScale().getVisibleLogicalRange(); return range ? range.to - range.from : 0; })()"
            ) as Number).toDouble())
        }
        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    private fun pushPending() {
        if (disposed || !ready || pendingKey.isBlank()) return
        webView?.engine?.executeScript("window.marketChartUpdate($pendingKey,$pendingData)")
        framesRequested = 3
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
    let drag = null;
    window.marketChartPointer = (type, x, y, buttons) => {
      const scale = chart.timeScale();
      if (type === 'mousedown' && (buttons & 1)) {
        drag = { x, y, range: scale.getVisibleLogicalRange(), spacing: scale.options().barSpacing };
        return null;
      }
      if (type === 'mousemove' && drag && (buttons & 1) && drag.range) {
        const offset = (drag.x - x) / drag.spacing;
        scale.setVisibleLogicalRange({ from: drag.range.from + offset, to: drag.range.to + offset });
        return null;
      }
      if (type === 'mouseup') {
        const clicked = drag && Math.abs(x - drag.x) + Math.abs(y - drag.y) < 4;
        drag = null;
        if (clicked) {
          const link = document.elementFromPoint(x, y)?.closest('a');
          if (link) return link.href;
        }
      }
      if (type === 'mouseleave' || x < 0 || x > scale.width() || y < 0 || y > document.getElementById('chart').clientHeight) {
        chart.clearCrosshairPosition();
        return null;
      }
      const time = scale.coordinateToTime(x);
      const price = series.coordinateToPrice(y);
      if (time !== null && price !== null) chart.setCrosshairPosition(price, time, series);
      return null;
    };
    window.marketChartWheel = (x, y, delta) => {
      const scale = chart.timeScale();
      const range = scale.getVisibleLogicalRange();
      if (!range) return;
      const ratio = Math.max(0, Math.min(1, x / scale.width()));
      const span = range.to - range.from;
      const anchor = range.from + span * ratio;
      const nextSpan = Math.max(0.2, span * Math.exp(delta * 0.001));
      scale.setVisibleLogicalRange({ from: anchor - nextSpan * ratio, to: anchor + nextSpan * (1 - ratio) });
    };
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
