"""Validate, deduplicate, and merge Kolai instruction JSONL shards.

This tool never invents examples. It only accepts authored/reviewed records and
keeps provenance fields when present. Exact duplicate prompts are rejected.
"""
import argparse
import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path

REQUIRED = ("instruction", "output")
PLACEHOLDERS = ("lorem ipsum", "TODO: answer", "вставьте ответ", "пример ответа здесь")


def normalize(value):
    value = unicodedata.normalize("NFKC", str(value)).casefold()
    return re.sub(r"\s+", " ", value).strip()


def validate_record(item, source, line_no):
    if not isinstance(item, dict):
        raise ValueError(f"{source}:{line_no}: record must be a JSON object")
    for key in REQUIRED:
        if not isinstance(item.get(key), str) or not item[key].strip():
            raise ValueError(f"{source}:{line_no}: '{key}' must be a non-empty string")
    if not isinstance(item.get("input", ""), str):
        raise ValueError(f"{source}:{line_no}: 'input' must be a string")
    instruction = item["instruction"].strip()
    answer = item["output"].strip()
    if len(instruction) < 8:
        raise ValueError(f"{source}:{line_no}: instruction is suspiciously short")
    if len(answer) < 25:
        raise ValueError(f"{source}:{line_no}: answer is too short to teach reliably")
    lowered = answer.casefold()
    if any(marker in lowered for marker in PLACEHOLDERS):
        raise ValueError(f"{source}:{line_no}: placeholder text detected")
    # Keep unknown metadata, but normalize the three training fields.
    cleaned = dict(item)
    cleaned["instruction"] = instruction
    cleaned["input"] = item.get("input", "").strip()
    cleaned["output"] = answer
    return cleaned


def prompt_key(item):
    raw = "\n".join(normalize(item.get(k, "")) for k in ("instruction", "input"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", nargs="+", required=True, help="JSONL files or directories")
    parser.add_argument("--output", default="ml/kolzailm/data/instructions.clean.jsonl")
    parser.add_argument("--allow-duplicate-prompts", action="store_true")
    args = parser.parse_args()

    files = []
    for value in args.input:
        path = Path(value)
        if path.is_dir():
            files.extend(sorted(path.glob("*.jsonl")))
        elif path.is_file():
            files.append(path)
        else:
            raise SystemExit(f"Input does not exist: {path}")
    files = sorted(set(files))
    if not files:
        raise SystemExit("No JSONL files found")

    accepted = []
    seen = {}
    duplicates = 0
    for path in files:
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            try:
                item = validate_record(json.loads(line), path, line_no)
            except (json.JSONDecodeError, ValueError) as exc:
                raise SystemExit(str(exc))
            key = prompt_key(item)
            if key in seen:
                duplicates += 1
                if not args.allow_duplicate_prompts:
                    print(f"Duplicate prompt: {path}:{line_no} matches {seen[key]}", file=sys.stderr)
                continue
            seen[key] = f"{path}:{line_no}"
            accepted.append(item)

    if not accepted:
        raise SystemExit("No unique valid examples found")
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="\n") as stream:
        for item in accepted:
            stream.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"Input files: {len(files)}")
    print(f"Accepted unique examples: {len(accepted)}")
    print(f"Skipped duplicate prompts: {duplicates}")
    print(f"Output: {target}")
    print("Note: this checks structure and exact prompt duplicates, not factual correctness.")


if __name__ == "__main__":
    main()
