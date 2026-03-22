"""
ARGUS Edge Agent - SQLite Ring Buffer
Stores normalised sensor events locally for offline resilience.
Capacity: configurable hours of data (default 4hr).
Replays to SQS on cloud reconnection.
ADR-004: ring buffer is temporary local storage only.
         All events flow to S3+Athena via SQS->Lambda->DynamoDB.
"""
import json
import logging
import sqlite3
import time
from pathlib import Path
from typing import List

from .config import RING_BUFFER_HOURS, RING_BUFFER_PATH

log = logging.getLogger(__name__)

CREATE_TABLE = """
PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS sensor_events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_json  TEXT    NOT NULL,
    created_at  INTEGER NOT NULL,
    replayed    INTEGER NOT NULL DEFAULT 0,
    replayed_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_created_at
    ON sensor_events(created_at);
CREATE INDEX IF NOT EXISTS idx_replayed
    ON sensor_events(replayed);
"""


class RingBuffer:
    def __init__(self, db_path: str = RING_BUFFER_PATH, max_hours: int = RING_BUFFER_HOURS):
        self.db_path = db_path
        self.max_secs = max_hours * 3600
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, timeout=10)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._conn() as conn:
            conn.executescript(CREATE_TABLE)
        log.info("RingBuffer initialised at %s", self.db_path)

    def write(self, event: dict) -> bool:
        """Write a normalised event to the ring buffer."""
        try:
            with self._conn() as conn:
                conn.execute(
                    "INSERT INTO sensor_events (event_json, created_at) VALUES (?, ?)",
                    (json.dumps(event), int(time.time())),
                )
            return True
        except sqlite3.Error as exc:
            log.error("RingBuffer.write failed: %s", exc)
            return False

    def get_unreplayed(self, limit: int = 100) -> List[dict]:
        """
        Return up to `limit` events not yet replayed to SQS.
        Oldest first to preserve chronological replay order.
        """
        try:
            with self._conn() as conn:
                rows = conn.execute(
                    "SELECT id, event_json FROM sensor_events "
                    "WHERE replayed = 0 ORDER BY created_at ASC LIMIT ?",
                    (limit,),
                ).fetchall()
            return [{"_buf_id": row["id"], **json.loads(row["event_json"])} for row in rows]
        except (sqlite3.Error, json.JSONDecodeError) as exc:
            log.error("RingBuffer.get_unreplayed failed: %s", exc)
            return []

    def mark_replayed(self, buf_ids: List[int]) -> None:
        """Mark events as successfully replayed to SQS."""
        if not buf_ids:
            return
        try:
            placeholders = ",".join("?" * len(buf_ids))
            with self._conn() as conn:
                conn.execute(
                    f"UPDATE sensor_events SET replayed = 1, replayed_at = ? WHERE id IN ({placeholders})",
                    [int(time.time()), *buf_ids],
                )
        except sqlite3.Error as exc:
            log.error("RingBuffer.mark_replayed failed: %s", exc)

    def purge_old(self) -> None:
        """
        Delete events older than max_hours that have been replayed.
        Keeps unreplayed events regardless of age as a safety net.
        """
        cutoff = int(time.time()) - self.max_secs
        try:
            with self._conn() as conn:
                deleted = conn.execute(
                    "DELETE FROM sensor_events WHERE created_at < ? AND replayed = 1",
                    (cutoff,),
                ).rowcount
            if deleted:
                log.info("RingBuffer.purge_old: deleted %d events", deleted)
        except sqlite3.Error as exc:
            log.error("RingBuffer.purge_old failed: %s", exc)

    def stats(self) -> dict:
        """Return ring buffer statistics."""
        try:
            with self._conn() as conn:
                total = conn.execute("SELECT COUNT(*) FROM sensor_events").fetchone()[0]
                unreplayed = conn.execute(
                    "SELECT COUNT(*) FROM sensor_events WHERE replayed = 0"
                ).fetchone()[0]
                oldest = conn.execute(
                    "SELECT MIN(created_at) FROM sensor_events WHERE replayed = 0"
                ).fetchone()[0]
            return {
                "total_events": total,
                "unreplayed_events": unreplayed,
                "oldest_unreplayed": oldest,
                "buffer_path": self.db_path,
                "max_hours": self.max_secs // 3600,
            }
        except sqlite3.Error as exc:
            log.error("RingBuffer.stats failed: %s", exc)
            return {}
