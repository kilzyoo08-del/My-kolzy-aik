"""Prepare user-supplied Kolai corpora without pretending to verify factual truth.

Usage:
  python ml/kolzailm/prepare_external_corpora.py --general path/to/general_clean.jsonl --coding path/to/coding_clean.jsonl --out-dir ml/kolzailm/data/external

The script validates JSONL shape, normalizes whitespace, removes exact duplicate
(instruction, input, output) triples, and writes separate corpus files plus a
manifest. It intentionally does NOT merge general and coding data or claim
answers are factually correct. Review licensing before training.
"""
import argparse
import hashlib
import json
from pathlib import Path


def prepare(source: Path, destination: Path):
    seen = set()
    counts = {"read": 0, "kept": 0, "invalid_json": 0, "invalid_schema": 0, "empty": 0, "exact_duplicates": 0}
    destination.parent.mkdir(parents=True, exist_ok=True)
    with source.open("r", encoding="utf-8", errors="strict") as src, destination.open("w", encoding="utf-8", newline="\n") as dst:
        for line_no, line in enumerate(src, 1):
            if not line.strip():
                continue
            counts["read"] += 1
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                counts["invalid_json"] += 1
                continue
            if not isinstance(item, dict) or not isinstance(item.get("instruction"), str) or not isinstance(item.get("output"), str) or not isinstance(item.get("input", ""), str):
                counts["invalid_schema"] += 1
                continue
            row = {
                "instruction": " ".join(item["instruction"].split()),
                "input": " ".join(item.get("input", "").split()),
                "output": item["output"].strip(),
            }
            if not row["instruction"] or not row["output"]:
                counts["empty"] += 1
                continue
            key = json.dumps(row, ensure_ascii=False, sort_keys=True)
            digest = hashlib.sha256(key.encode("utf-8")).hexdigest()
            if digest in seen:
                counts["exact_duplicates"] += 1
                continue
            seen.add(digest)
            dst.write(json.dumps(row, ensure_ascii=False) + "\n")
            counts["kept"] += 1
    counts["output_sha256"] = hashlib.sha256(destination.read_bytes()).hexdigest()
    counts["output_bytes"] = destination.stat().st_size
    return counts


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--general", required=True, type=Path)
    p.add_argument("--coding", required=True, type=Path)
    p.add_argument("--out-dir", default="ml/kolzailm/data/external", type=Path)
    args = p.parse_args()

    report = {
        "status": "format_checked_only_answers_not_fact_verified",
        "license_note": "Confirm that each source dataset is licensed for model training.",
        "corpora": {}
    }
    for name, source in (("general", args.general), ("coding", args.coding)):
        if not source.is_file():
            raise SystemExit(f"Input file not found: {source}")
        target = args.out_dir / f"{name}.jsonl"
        report["corpora"][name] = {
            "source": str(source),
            "output": str(target),
            **prepare(source, target)
        }
    args.out_dir.mkdir(parents=True, exist_ok=True)
    manifest = args.out_dir / "manifest.json"
    manifest.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    print("Reminder: syntax validation is not factual or code correctness review.")


if __name__ == "__main__":
    main()
