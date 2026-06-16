from __future__ import annotations

import asyncio
import json
import threading
import traceback
from collections import deque
from typing import Any

import aiohttp  # noqa: F401


SUPPORTED_EVENTS = (
    "DANMU_MSG",
    "SEND_GIFT",
    "COMBO_SEND",
    "GUARD_BUY",
    "SUPER_CHAT_MESSAGE",
    "SUPER_CHAT_MESSAGE_JPN",
    "USER_TOAST_MSG",
    "LIKE_INFO_V3_CLICK",
    "LIKE_INFO_V3_UPDATE",
)


class ThirdPartyRuntime:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._events: deque[str] = deque()
        self._state = "idle"
        self._last_error = ""
        self._room_id: int | None = None
        self._thread: threading.Thread | None = None
        self._loop: asyncio.AbstractEventLoop | None = None
        self._live_danmaku: Any | None = None
        self._stop_requested = False

    def start(self, room_id: str | int) -> None:
        resolved_room_id = int(room_id)

        self.stop()

        with self._lock:
            self._events.clear()
            self._state = "connecting"
            self._last_error = ""
            self._room_id = resolved_room_id
            self._stop_requested = False
            self._thread = threading.Thread(
                target=self._thread_main,
                args=(resolved_room_id,),
                daemon=True,
                name="bilibili-third-party-runtime",
            )
            self._thread.start()

    def drain_events(self, limit: int = 50) -> list[str]:
        items: list[str] = []
        max_items = max(1, int(limit))
        with self._lock:
            while self._events and len(items) < max_items:
                items.append(self._events.popleft())
        return items

    def get_status_json(self) -> str:
        with self._lock:
            return json.dumps(
                {
                    "state": self._state,
                    "last_error": self._last_error,
                    "room_id": self._room_id,
                },
                ensure_ascii=False,
            )

    def stop(self) -> None:
        thread: threading.Thread | None = None
        loop: asyncio.AbstractEventLoop | None = None

        with self._lock:
            thread = self._thread
            loop = self._loop
            if thread is None:
                self._state = "idle"
                self._room_id = None
                return
            self._stop_requested = True
            self._state = "stopping"

        if loop is not None:
            future = asyncio.run_coroutine_threadsafe(self._disconnect_live_danmaku(), loop)
            try:
                future.result(timeout=5)
            except Exception:
                pass

        if thread is not None and thread.is_alive():
            thread.join(timeout=5)

        with self._lock:
            self._thread = None
            self._loop = None
            self._live_danmaku = None
            self._room_id = None
            self._state = "idle"

    def _thread_main(self, room_id: int) -> None:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        with self._lock:
            self._loop = loop

        try:
            loop.run_until_complete(self._run_live_danmaku(room_id))
            with self._lock:
                if not self._stop_requested and self._state != "error":
                    self._state = "idle"
        except Exception as exc:
            if not self._stop_requested:
                self._set_error(exc)
        finally:
            pending = asyncio.all_tasks(loop)
            for task in pending:
                task.cancel()
            if pending:
                loop.run_until_complete(asyncio.gather(*pending, return_exceptions=True))
            loop.close()
            with self._lock:
                self._loop = None
                self._live_danmaku = None
                self._thread = None

    async def _run_live_danmaku(self, room_id: int) -> None:
        self._ensure_supported_client_selected()
        live_danmaku = self._create_live_danmaku(room_id)
        with self._lock:
            self._live_danmaku = live_danmaku
            self._state = "running"

        for event_name in SUPPORTED_EVENTS:
            self._register_handler(live_danmaku=live_danmaku, event_name=event_name)

        await live_danmaku.connect()

    async def _disconnect_live_danmaku(self) -> None:
        live_danmaku = None
        with self._lock:
            live_danmaku = self._live_danmaku
        if live_danmaku is not None:
            await live_danmaku.disconnect()

    def _create_live_danmaku(self, room_id: int) -> Any:
        from bilibili_api.live import LiveDanmaku

        return LiveDanmaku(room_id, debug=False, max_retry=5, retry_after=1)

    def _ensure_supported_client_selected(self) -> None:
        from bilibili_api import get_registered_clients, get_selected_client, select_client

        current_name = ""
        try:
            current_name, _ = get_selected_client()
        except Exception:
            current_name = ""

        if current_name in {"aiohttp", "curl_cffi"}:
            return

        registered_clients = get_registered_clients()
        for candidate in ("aiohttp", "curl_cffi"):
            if candidate in registered_clients:
                select_client(candidate)
                return

        raise RuntimeError("第三方房间消息流需要安装 aiohttp 或 curl_cffi 作为 WebSocket 请求后端")

    def _register_handler(self, *, live_danmaku: Any, event_name: str) -> None:
        @live_danmaku.on(event_name)
        async def _handler(event: dict[str, Any]) -> None:
            raw_message = event.get("data", event)
            if not isinstance(raw_message, dict):
                return
            message = dict(raw_message)
            message.setdefault("cmd", event_name)
            mapped = map_third_party_message(message, room_id=self._room_id or 0)
            if mapped is None:
                return
            with self._lock:
                self._events.append(json.dumps(mapped, ensure_ascii=False))

    def _set_error(self, exc: Exception) -> None:
        with self._lock:
            self._state = "error"
            self._last_error = "".join(
                traceback.format_exception_only(type(exc), exc),
            ).strip()


def map_third_party_message(message: dict[str, Any], *, room_id: int) -> dict[str, Any] | None:
    cmd = str(message.get("cmd", ""))
    if not cmd:
        return None

    if cmd in {"SEND_GIFT", "COMBO_SEND"}:
        data = _as_dict(message.get("data"))
        guard_level = _resolve_gift_guard_level(data)
        return {
            "source": "third_party_ws",
            "event_type": "gift",
            "cmd": cmd,
            "room_id": room_id,
            "open_id": "",
            "uname": str(data.get("uname", "")),
            "timestamp": _as_int(data.get("timestamp")),
            "payload": {
                "gift_id": _as_int(data.get("giftId") or data.get("gift_id")),
                "gift_name": str(data.get("giftName") or data.get("gift_name") or ""),
                "gift_num": _as_int(data.get("combo_num") or data.get("num") or data.get("gift_num") or 0),
                "price": _as_int(data.get("price")),
                "r_price": _as_int(
                    data.get("combo_total_coin")
                    or data.get("total_coin")
                    or data.get("r_price")
                    or data.get("price"),
                ),
                "guard_level": guard_level,
                "guard_label": _guard_level_to_label(guard_level),
            },
        }

    if cmd == "GUARD_BUY":
        data = _as_dict(message.get("data"))
        guard_level = _resolve_guard_level_from_name(
            data.get("gift_name") or data.get("giftName") or data.get("role_name"),
        )
        return _build_gift_event(
            cmd=cmd,
            event_type="guard_buy",
            room_id=room_id,
            uname=str(data.get("username") or data.get("uname") or ""),
            timestamp=_as_int(data.get("start_time") or data.get("timestamp")),
            payload={
                "gift_id": _as_int(data.get("gift_id") or data.get("giftId")),
                "gift_name": str(data.get("gift_name") or data.get("giftName") or data.get("role_name") or "大航海"),
                "gift_num": _as_int(data.get("num") or 1),
                "price": _as_int(data.get("price")),
                "r_price": _as_int(data.get("price")),
                "guard_level": guard_level,
                "guard_label": _guard_level_to_label(guard_level),
            },
        )

    if cmd in {"SUPER_CHAT_MESSAGE", "SUPER_CHAT_MESSAGE_JPN"}:
        data = _as_dict(message.get("data"))
        gift = _as_dict(data.get("gift"))
        user_info = _as_dict(data.get("user_info"))
        uinfo = _as_dict(data.get("uinfo"))
        base_info = _as_dict(uinfo.get("base"))
        return _build_gift_event(
            cmd=cmd,
            event_type="super_chat",
            room_id=room_id,
            uname=str(user_info.get("uname") or base_info.get("name") or base_info.get("uname") or ""),
            timestamp=_as_int(data.get("ts") or data.get("start_time") or data.get("send_time")),
            payload={
                "gift_id": _as_int(gift.get("gift_id") or 12000),
                "gift_name": str(gift.get("gift_name") or "醒目留言"),
                "gift_num": _as_int(gift.get("num") or 1),
                "price": _as_int(data.get("price")),
                "r_price": _as_int(data.get("price")),
                "message": str(data.get("message") or ""),
                "guard_level": 0,
                "guard_label": "",
            },
        )

    if cmd == "USER_TOAST_MSG":
        data = _as_dict(message.get("data"))
        guard_level = _resolve_guard_level_from_name(
            data.get("gift_name") or data.get("giftName") or data.get("role_name"),
        )
        return _build_gift_event(
            cmd=cmd,
            event_type="guard_renew",
            room_id=room_id,
            uname=str(data.get("username") or data.get("uname") or ""),
            timestamp=_as_int(data.get("start_time") or data.get("timestamp")),
            payload={
                "gift_id": _as_int(data.get("gift_id") or data.get("giftId")),
                "gift_name": str(data.get("gift_name") or data.get("giftName") or data.get("role_name") or "庆祝消息"),
                "gift_num": _as_int(data.get("num") or 1),
                "price": _as_int(data.get("price")),
                "r_price": _as_int(data.get("price")),
                "toast_msg": str(data.get("toast_msg") or ""),
                "guard_level": guard_level,
                "guard_label": _guard_level_to_label(guard_level),
            },
        )

    if cmd == "DANMU_MSG":
        info = message.get("info", [])
        content = ""
        uname = ""
        timestamp = _as_int(message.get("timestamp"))
        guard_level = 0
        if isinstance(info, list):
            if len(info) > 1:
                content = str(info[1] or "")
            if len(info) > 2 and isinstance(info[2], list) and len(info[2]) > 1:
                uname = str(info[2][1] or "")
            if len(info) > 0 and isinstance(info[0], list) and len(info[0]) > 4:
                timestamp = _as_int(info[0][4])
            guard_level = _extract_danmaku_guard_level(info)
        return {
            "source": "third_party_ws",
            "event_type": _resolve_danmaku_event_type(guard_level),
            "cmd": cmd,
            "room_id": room_id,
            "open_id": "",
            "uname": uname,
            "timestamp": timestamp,
            "payload": {
                "msg": content,
                "guard_level": guard_level,
                "guard_label": _guard_level_to_label(guard_level),
            },
        }

    if cmd in {"LIKE_INFO_V3_CLICK", "LIKE_INFO_V3_UPDATE"}:
        data = _as_dict(message.get("data"))
        like_count = _resolve_like_count(data)
        return {
            "source": "third_party_ws",
            "event_type": "like",
            "cmd": cmd,
            "room_id": room_id,
            "open_id": "",
            "uname": str(data.get("uname", "")),
            "timestamp": _as_int(data.get("timestamp")),
            "payload": {
                "like_text": str(data.get("like_text") or "点赞"),
                "like_count": like_count,
                "like_delta": 1 if cmd == "LIKE_INFO_V3_CLICK" else 0,
            },
        }

    return None


def _build_gift_event(
    *,
    cmd: str,
    event_type: str,
    room_id: int,
    uname: str,
    timestamp: int,
    payload: dict[str, Any],
) -> dict[str, Any]:
    return {
        "source": "third_party_ws",
        "event_type": event_type,
        "cmd": cmd,
        "room_id": room_id,
        "open_id": "",
        "uname": uname,
        "timestamp": timestamp,
        "payload": payload,
    }


def _resolve_like_count(data: dict[str, Any]) -> int:
    for key in ("like_count", "click_count", "count"):
        if key in data:
            return _as_int(data.get(key))
    return 0


def _as_int(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def _as_dict(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    return {}


def _extract_danmaku_guard_level(info: list[Any]) -> int:
    try:
        direct_guard_level = _as_int(info[7])
        if direct_guard_level > 0:
            return direct_guard_level
    except (IndexError, TypeError):
        pass

    try:
        medal_guard_level = _as_int(info[3][10])
        if medal_guard_level > 0:
            return medal_guard_level
    except (IndexError, TypeError):
        pass

    try:
        nested_medal = _as_dict(info[3][0])
        return _as_int(nested_medal.get("guard_level"))
    except (IndexError, TypeError):
        return 0


def _resolve_danmaku_event_type(guard_level: int) -> str:
    # Android 端需要区分舰队弹幕类型，后续规则页才能按普通 / 舰长 / 提督 / 总督分别建规则。
    return {
        3: "danmaku_captain",
        2: "danmaku_commander",
        1: "danmaku_governor",
    }.get(_as_int(guard_level), "danmaku")


def _guard_level_to_label(guard_level: int) -> str:
    return {
        1: "总督",
        2: "提督",
        3: "舰长",
    }.get(_as_int(guard_level), "")


def _resolve_gift_guard_level(data: dict[str, Any]) -> int:
    direct_level = _as_int(data.get("guard_level"))
    if direct_level > 0:
        return direct_level

    medal_info = _as_dict(data.get("medal_info"))
    medal_level = _as_int(medal_info.get("guard_level"))
    if medal_level > 0:
        return medal_level

    uinfo = _as_dict(data.get("uinfo"))
    uinfo_level = _as_int(uinfo.get("guard_level"))
    if uinfo_level > 0:
        return uinfo_level

    return 0


def _resolve_guard_level_from_name(value: Any) -> int:
    normalized = str(value or "").strip()
    if normalized == "总督":
        return 1
    if normalized == "提督":
        return 2
    if normalized == "舰长":
        return 3
    return 0


def create_runtime() -> ThirdPartyRuntime:
    return ThirdPartyRuntime()
