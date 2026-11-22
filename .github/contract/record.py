"""Appends one row per platform to CONTRACT.md, but only if something changed since its last row.

Usage: record.py <directory with observation files> <CONTRACT.md>

Each observation file holds `key: value` lines: `platform`, `script`, `native`, `lts`,
`java <major>` and `result`, written by the SDKMAN contract workflow.
"""
import datetime
import sys
from pathlib import Path

LTS = ["8", "11", "17", "21", "25"]
HEADER = """# SDKMAN contract

What the weekly [SDKMAN contract check](.github/workflows/sdkman-contract.yml) saw.
A row is only added when something changed for a platform: SDKMAN version, LTS versions,
newest releases or the result. *Newest* lists the newest release per LTS line for
Temurin / Zulu / Corretto (`-` = none).

| Date | Platform | SDKMAN script | SDKMAN native | LTS (foojay) | Newest (tem / zulu / amzn) | Result |
|---|---|---|---|---|---|---|
"""


def read(path: Path) -> dict:
    values = {}
    for line in path.read_text().splitlines():
        key, sep, value = line.partition(":")
        if sep:
            values[key.strip()] = value.strip()
    return values


def cells(obs: dict) -> list:
    newest = "<br>".join(
        f"{major}: " + " / ".join(f"`{v}`" if v != "-" else v for v in obs[f"java {major}"].split(", "))
        for major in LTS if f"java {major}" in obs
    )
    return [obs.get("platform") or obs.get("os", "?"), obs.get("script", "?"), obs.get("native", "-"),
            obs.get("lts", "?"), newest or "?", obs.get("result", "?")]


def last_rows(text: str) -> dict:
    rows = {}
    for line in text.splitlines():
        parts = [p.strip() for p in line.strip().strip("|").split("|")]
        if line.startswith("|") and len(parts) == 7 and parts[0][:1].isdigit():
            rows[parts[1]] = parts[1:]
    return rows


def main(observations: Path, contract: Path) -> None:
    text = contract.read_text() if contract.exists() else HEADER
    previous = last_rows(text)
    today = datetime.date.today().isoformat()
    added = []
    for path in sorted(observations.rglob("*.txt")):
        row = cells(read(path))
        if previous.get(row[0]) != row:
            added.append("| " + " | ".join([today] + row) + " |")
    if added:
        contract.write_text(text.rstrip("\n") + "\n" + "\n".join(added) + "\n")
    print(f"{len(added)} new row(s)")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
