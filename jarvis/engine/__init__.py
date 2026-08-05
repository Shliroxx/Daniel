"""Denkapparat — austauschbare Backends fuer Claude."""

from __future__ import annotations

from ..config import config
from .base import Engine, Emit


def build_engine(user_name: str = "Daniel") -> Engine:
    """Waehlt das Backend anhand von JARVIS_BACKEND."""
    if config.backend == "api":
        from .api_backend import ApiEngine

        return ApiEngine(user_name)

    from .cli_backend import CliEngine

    return CliEngine(user_name)


__all__ = ["Engine", "Emit", "build_engine"]
