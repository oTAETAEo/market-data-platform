import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path


WORKER = Path(__file__).parents[2] / "main" / "resources" / "tradingagents_worker.py"
SPEC = importlib.util.spec_from_file_location("desktop_tradingagents_worker", WORKER)
worker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(worker)


class WorkerTests(unittest.TestCase):
    def candles(self, count=200):
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        return [{
            "symbol": "BTCUSDT", "closed": True,
            "openTime": (start + timedelta(minutes=15 * index)).isoformat(),
            "open": "100", "high": "103", "low": "99",
            "close": str(100 + index / 10), "volume": "10",
        } for index in range(count)]

    def test_builds_closed_snapshot_and_rejects_mismatch(self):
        frame = worker.candles_frame({"symbol": "BTCUSDT", "candles": self.candles()})
        self.assertEqual(len(frame), 200)
        mismatched = self.candles()
        mismatched[-1]["symbol"] = "ETHUSDT"
        with self.assertRaisesRegex(ValueError, "일치하지"):
            worker.candles_frame({"symbol": "BTCUSDT", "candles": mismatched})

    def test_maps_reports_and_requires_explicit_rating(self):
        state = {
            "market_report": "technical", "trader_investment_plan": "trade",
            "final_trade_decision": "**Rating**: Buy\n\nresult",
            "investment_debate_state": {"bull_history": "bull", "bear_history": "bear"},
            "risk_debate_state": {"aggressive_history": "a", "conservative_history": "c", "neutral_history": "n"},
        }
        reports = worker.report_messages(state)
        self.assertEqual(len(reports), 9)
        self.assertEqual(worker.decision_from(state), "BUY")
        self.assertEqual(worker.decision_from({"final_trade_decision": "unclear"}), "UNRESOLVED")
        self.assertEqual(next(r for r in reports if r["agentName"] == "News Analyst")["status"], "SKIPPED")


if __name__ == "__main__":
    unittest.main()
