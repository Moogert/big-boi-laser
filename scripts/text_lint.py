from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PROHIBITED = {
    "activate", "engage", "go", "inactive", "initiate", "normal", "online",
    "operate", "powerful", "proceed", "ready", "robust", "run", "seamless",
    "utilize", "working", "world-class",
}
GENERIC_ACTIONS = {"ok", "yes", "confirm"}
CONTRACTION = re.compile(r"\b(?:can't|don't|doesn't|isn't|it's|won't|shouldn't|couldn't|wouldn't)\b", re.I)


def operator_strings(root: Path) -> list[tuple[str, str]]:
    result: list[tuple[str, str]] = []
    for path in root.glob("app/src/main/res/values*/strings.xml"):
        for node in ET.parse(path).getroot().findall("string"):
            result.append((f"{path}:{node.attrib.get('name', 'UNKNOWN')}", "".join(node.itertext()).strip()))
    return result


def lint(source: str, text: str) -> list[str]:
    errors: list[str] = []
    lowered = text.lower()
    if ";" in text:
        errors.append("contains a semicolon")
    if "—" in text:
        errors.append("contains an em dash")
    if CONTRACTION.search(text):
        errors.append("contains a contraction")
    words = set(re.findall(r"[a-z]+(?:-[a-z]+)?", lowered))
    for word in sorted(words & PROHIBITED):
        errors.append(f"contains prohibited word: {word}")
    if lowered.strip() in GENERIC_ACTIONS:
        errors.append("uses a generic hazardous-action label")
    for sentence in re.split(r"(?<=[.!?])\s+", text):
        count = len(re.findall(r"\b[\w'-]+\b", sentence))
        if count > 20:
            errors.append(f"sentence has {count} words")
    if re.search(r"\b(?:fault|alarm)\s+code\s*:\s*[A-Z]?\d+\b", text, re.I) and len(text.split(".")) < 2:
        errors.append("shows a fault code without an explanation")
    return [f"{source}: {error}: {text}" for error in errors]


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    errors = [error for source, text in operator_strings(root) for error in lint(source, text)]
    if errors:
        print("\n".join(errors))
        return 1
    print("Operator text lint passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

