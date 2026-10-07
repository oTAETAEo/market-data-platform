#!/usr/bin/env python3
"""One-shot JSONL bridge from the desktop JVM to the local TradingAgents graph."""

from __future__ import annotations

import argparse
import io
import json
import logging
import os
import re
import sys
import tempfile
from contextlib import redirect_stdout
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PROTOCOL_STDOUT = sys.stdout

KEY_ENV = {
    "openai": "OPENAI_API_KEY", "anthropic": "ANTHROPIC_API_KEY",
    "google": "GOOGLE_API_KEY", "azure": "AZURE_OPENAI_API_KEY",
    "xai": "XAI_API_KEY", "deepseek": "DEEPSEEK_API_KEY",
    "qwen": "DASHSCOPE_API_KEY", "qwen-cn": "DASHSCOPE_CN_API_KEY",
    "glm": "ZHIPU_API_KEY", "glm-cn": "ZHIPU_CN_API_KEY",
    "minimax": "MINIMAX_API_KEY", "minimax-cn": "MINIMAX_CN_API_KEY",
    "openrouter": "OPENROUTER_API_KEY", "mistral": "MISTRAL_API_KEY",
    "kimi": "MOONSHOT_API_KEY", "groq": "GROQ_API_KEY",
    "nvidia": "NVIDIA_API_KEY", "openai_compatible": "OPENAI_COMPATIBLE_API_KEY",
}


def emit(message: dict[str, Any]) -> None:
    print(json.dumps(message, ensure_ascii=False, separators=(",", ":")),
          file=PROTOCOL_STDOUT, flush=True)


def settings_from(payload: dict[str, Any]) -> dict[str, Any]:
    settings = payload.get("settings") or {}
    provider = str(settings.get("provider") or "openai").strip().lower()
    quick = str(settings.get("quickModel") or "").strip()
    deep = str(settings.get("deepModel") or "").strip()
    api_key = str(settings.get("apiKey") or "").strip()
    for env_name in set(KEY_ENV.values()):
        os.environ.pop(env_name, None)
    required = KEY_ENV.get(provider)
    if required and api_key:
        os.environ[required] = api_key
    if provider not in {"ollama", "openai_compatible", "bedrock"} and required and not api_key:
        raise ValueError("선택한 AI 제공자의 API 키를 입력해 주세요.")
    if not quick or not deep:
        raise ValueError("빠른 모델과 심층 모델 이름을 입력해 주세요.")
    return {"provider": provider, "quick": quick, "deep": deep, "keyConfigured": bool(api_key)}


def candles_frame(context: dict[str, Any]):
    import pandas as pd

    expected = str(context.get("symbol") or "").upper()
    rows = []
    now = datetime.now(timezone.utc)
    for candle in context.get("candles") or []:
        if str(candle.get("symbol") or "").upper() != expected:
            raise ValueError("분석 종목과 캔들 종목이 일치하지 않습니다.")
        if not candle.get("closed"):
            continue
        opened = pd.to_datetime(candle["openTime"], utc=True)
        if opened.to_pydatetime() > now:
            raise ValueError("미래 시점 캔들은 분석할 수 없습니다.")
        row = {
            "Date": opened.tz_convert(None),
            "Open": float(candle["open"]), "High": float(candle["high"]),
            "Low": float(candle["low"]), "Close": float(candle["close"]),
            "Volume": float(candle["volume"]),
        }
        if row["High"] < row["Low"] or row["Volume"] < 0:
            raise ValueError("잘못된 OHLCV 캔들이 포함되어 있습니다.")
        rows.append(row)
    frame = pd.DataFrame(rows).drop_duplicates("Date", keep="last").sort_values("Date")
    if len(frame) < 200:
        raise ValueError("분석에 필요한 확정 캔들이 부족합니다. 최소 200개가 필요합니다.")
    return frame


def install_snapshot_tools(frame, config: dict[str, Any]) -> None:
    from stockstats import wrap
    from tradingagents.dataflows import interface
    from tradingagents.dataflows import market_data_validator

    def stock_data(symbol: str, start_date: str, end_date: str) -> str:
        selected = frame[(frame["Date"] >= start_date) & (frame["Date"] <= end_date)]
        return selected.to_csv(index=False)

    def indicator(symbol: str, name: str, curr_date: str, look_back_days: int = 30) -> str:
        stock = wrap(frame.copy())
        try:
            stock[name]
            tail = stock[["date", "close", name]].tail(max(1, min(int(look_back_days), 200)))
            return tail.to_csv(index=False)
        except Exception as exc:
            return f"INDICATOR_UNAVAILABLE: {name} ({type(exc).__name__})"

    interface.VENDOR_METHODS["get_stock_data"]["desktop_snapshot"] = stock_data
    interface.VENDOR_METHODS["get_indicators"]["desktop_snapshot"] = indicator
    config["tool_vendors"] = {
        **config.get("tool_vendors", {}),
        "get_stock_data": "desktop_snapshot",
        "get_indicators": "desktop_snapshot",
    }
    market_data_validator.load_ohlcv = lambda symbol, curr_date: frame.copy()


def report_messages(state: dict[str, Any]) -> list[dict[str, str]]:
    debate = state.get("investment_debate_state") or {}
    risk = state.get("risk_debate_state") or {}
    risk_text = "\n\n".join(filter(None, [risk.get("aggressive_history"),
                                               risk.get("conservative_history"),
                                               risk.get("neutral_history")]))
    reports = [
        ("Technical Analyst", state.get("market_report"), "COMPLETED"),
        ("Sentiment Analyst", "실시간 소셜 데이터가 연결되지 않아 이번 분석에서 제외했습니다.", "SKIPPED"),
        ("News Analyst", "실시간 뉴스 데이터가 연결되지 않아 이번 분석에서 제외했습니다.", "SKIPPED"),
        ("Fundamentals Analyst", "암호화폐 선물 분석에서는 기업 펀더멘털 분석을 사용하지 않습니다.", "SKIPPED"),
        ("Bull Researcher", debate.get("bull_history"), "COMPLETED"),
        ("Bear Researcher", debate.get("bear_history"), "COMPLETED"),
        ("Trader Agent", state.get("trader_investment_plan"), "COMPLETED"),
        ("Risk Management", risk_text, "COMPLETED"),
        ("Portfolio Manager", state.get("final_trade_decision"), "COMPLETED"),
    ]
    return [{"agentName": name, "status": status,
             "summary": (text or "결과가 생성되지 않았습니다.").strip()}
            for name, text, status in reports]


def decision_from(state: dict[str, Any]) -> str:
    final = str(state.get("final_trade_decision") or "")
    match = re.search(r"\*\*Rating\*\*:\s*(Buy|Overweight|Hold|Underweight|Sell)", final, re.I)
    return match.group(1).upper() if match else "UNRESOLVED"


def analyze(payload: dict[str, Any], repo: Path) -> None:
    request_id = str(payload.get("requestId") or "")
    context = payload.get("context") or {}
    configured = settings_from(payload)
    frame = candles_frame(context)
    emit({"type": "progress", "requestId": request_id, "stage": "TradingAgents 초기화"})

    from tradingagents.default_config import DEFAULT_CONFIG
    from tradingagents.graph.trading_graph import TradingAgentsGraph

    with tempfile.TemporaryDirectory(prefix="market-agents-") as temp:
        config = dict(DEFAULT_CONFIG)
        config.update({
            "llm_provider": configured["provider"],
            "quick_think_llm": configured["quick"],
            "deep_think_llm": configured["deep"],
            "output_language": "Korean",
            "max_debate_rounds": 1,
            "max_risk_discuss_rounds": 1,
            "max_recur_limit": 80,
            "checkpoint_enabled": False,
            "results_dir": str(Path(temp) / "results"),
            "data_cache_dir": str(Path(temp) / "cache"),
            "memory_log_path": str(Path(temp) / "memory.md"),
        })
        install_snapshot_tools(frame, config)
        with redirect_stdout(io.StringIO()):
            graph = TradingAgentsGraph(selected_analysts=("market",), config=config)
        symbol = str(context["symbol"]).upper()
        timeframe = str(context["timeframe"])
        exchange = str(context["exchange"]).upper()
        latest = frame.iloc[-1]["Date"].strftime("%Y-%m-%d")
        instrument = (f"Instrument: {symbol} perpetual futures on {exchange}. Asset type: crypto. "
                      f"Quote currency: USDT. Supplied immutable {timeframe} snapshot contains "
                      f"{len(frame)} finalized candles through {frame.iloc[-1]['Date']}. "
                      "Use only the supplied snapshot for exact price and indicator claims. "
                      "This is analysis only; do not claim an order was executed.")
        initial = graph.propagator.create_initial_state(symbol, latest, asset_type="crypto",
                                                        instrument_context=instrument)
        args = graph.propagator.get_graph_args()
        final_state = initial
        stages = [("market_report", "기술적 분석"), ("investment_plan", "상승·하락 토론"),
                  ("trader_investment_plan", "트레이더 판단"),
                  ("final_trade_decision", "리스크 및 최종 판단")]
        emitted = set()
        with redirect_stdout(io.StringIO()):
            for state in graph.graph.stream(initial, **args):
                final_state = state
                for field, stage in stages:
                    if state.get(field) and field not in emitted:
                        emitted.add(field)
                        emit({"type": "progress", "requestId": request_id, "stage": stage})

    reports = report_messages(final_state)
    for report in reports:
        emit({"type": "report", "requestId": request_id, **report})
    emit({"type": "result", "requestId": request_id, "symbol": context["symbol"],
          "decision": decision_from(final_state),
          "risk": "REVIEWED" if any(r["agentName"] == "Risk Management" and r["summary"] for r in reports) else "UNRESOLVED",
          "generatedAt": datetime.now(timezone.utc).isoformat(), "reports": reports})


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    repo = Path(args.repo).expanduser().resolve()
    sys.path.insert(0, str(repo))
    logging.disable(logging.CRITICAL)
    line = ""
    try:
        if args.check:
            import tradingagents
            emit({"type": "ready", "pythonVersion": sys.version.split()[0], "repo": str(repo),
                  "tradingAgents": getattr(tradingagents, "__version__", "available")})
            return 0
        line = sys.stdin.readline()
        if not line:
            raise ValueError("분석 요청을 받지 못했습니다.")
        analyze(json.loads(line), repo)
        return 0
    except ValueError as exc:
        request_id = ""
        try:
            request_id = str(json.loads(line).get("requestId") or "")
        except Exception:
            pass
        emit({"type": "error", "requestId": request_id, "code": "CONFIGURATION", "message": str(exc)})
    except Exception:
        emit({"type": "error", "requestId": "", "code": "ANALYSIS_FAILED",
              "message": "TradingAgents 분석을 완료하지 못했습니다. 설정과 네트워크 상태를 확인해 주세요."})
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
