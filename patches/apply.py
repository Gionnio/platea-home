#!/usr/bin/env python3
"""Platea Home: the few edits to CleanYourTV's own code, applied to an extracted copy before the build.
Usage: python3 -I patches/apply.py <extracted cleanyourtv folder>
Each edit must match exactly once: if CleanYourTV changed that spot, this stops with a message and the
build script falls back to the last version that built."""
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
main = root / "launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java"


def insert_after(path, anchor, text):
    s = path.read_text(encoding="utf-8")
    n = s.count(anchor)
    if n != 1:
        sys.exit(f"platea-home patch: '{anchor.strip()}' found {n} times in {path.name} (expected 1)")
    path.write_text(s.replace(anchor, anchor + text), encoding="utf-8")


# 1. Top shelf: the line under the app's name says what the app is (PlateaHomeText); apps without a line
#    keep CleanYourTV's "Last opened".
insert_after(
    main,
    "private void updateHeroMeta(int position) {\n",
    "        String plateaLine = PlateaHomeText.line(this, adapter.packageAt(position));\n"
    "        if (plateaLine != null) { heroMeta.setText(plateaLine); heroMeta.setVisibility(View.VISIBLE); return; }\n",
)

print("platea-home patch: ok (1 edit)")
