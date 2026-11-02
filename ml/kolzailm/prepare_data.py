"""Convert instruction JSONL records into plain-text training sequences."""
import argparse
import json
from pathlib import Path

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="ml/kolzailm/data/instructions.jsonl")
    parser.add_argument("--output", default="ml/kolzailm/data/train.txt")
    args = parser.parse_args()
    source = Path(args.input)
    records = []
    for line_no, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as exc:
            raise SystemExit(f"Invalid JSON on line {line_no}: {exc}")
        instruction = str(item.get("instruction", "")).strip()
        answer = str(item.get("output", "")).strip()
        extra = str(item.get("input", "")).strip()
        if not instruction or not answer:
            raise SystemExit(f"Line {line_no} must contain non-empty instruction and output")
        prompt = instruction + ("\n" + extra if extra else "")
        records.append(f"Пользователь: {prompt}\nKolzai: {answer}\n")
    if not records:
        raise SystemExit("No valid training records found")
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(records), encoding="utf-8")
    print(f"Prepared {len(records)} dialogue examples -> {target}")

if __name__ == "__main__":
    main()
