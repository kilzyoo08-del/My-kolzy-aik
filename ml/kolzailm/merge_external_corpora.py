"""Merge the supplied General and Coding JSONL corpora locally for Kolai.

Raw corpora stay out of Git history. This script streams input files, validates
JSON/UTF-8/schema, removes exact duplicates, tags provenance, and writes one
merged JSONL plus a report. It does NOT fact-check answers.
"""
import argparse
import hashlib
import json
from pathlib import Path


def process(path, source, output, seen, report):
    digest = hashlib.sha256()
    with path.open("rb") as fin:
        for line_no, raw in enumerate(fin, 1):
            report["read"] += 1
            digest.update(raw)
            try:
                item = json.loads(raw.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                report["bad_json_or_utf8"] += 1
                continue
            if not isinstance(item, dict):
                report["not_object"] += 1
                continue
            instruction = item.get("instruction")
            answer = item.get("output")
            extra = item.get("input", "")
            if not isinstance(instruction, str) or not isinstance(answer, str) or not isinstance(extra, str):
                report["bad_schema"] += 1
                continue
            instruction, answer, extra = instruction.strip(), answer.strip(), extra.strip()
            if not instruction or not answer:
                report["empty_required_field"] += 1
                continue
            key = (instruction, extra, answer)
            if key in seen:
                report["exact_duplicates"] += 1
                continue
            seen.add(key)
            row = {
                "instruction": instruction,
                "input": extra,
                "output": answer,
                "source": source,
            }
            output.write(json.dumps(row, ensure_ascii=False) + "\\n")
            report["kept"] += 1
    report["input_sha256"] = digest.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--general", required=True, type=Path)
    p.add_argument("--coding", required=True, type=Path)
    p.add_argument("--output", default="ml/kolzailm/data/external_merged.jsonl", type=Path)
    args = p.parse_args()
    for path in (args.general, args.coding):
        if not path.is_file():
            raise SystemExit(f"File not found: {path}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    seen = set()
    reports = {}
    temp = args.output.with_suffix(args.output.suffix + ".tmp")
    try:
        with temp.open("w", encoding="utf-8", newline="\\n") as out:
            for name, path in (("general", args.general), ("coding", args.coding)):
                stats = {k: 0 for k in (
                    "read", "bad_json_or_utf8", "not_object", "bad_schema",
                    "empty_required_field", "exact_duplicates", "kept"
                )}
                process(path, name, out, seen, stats)
                reports[name] = stats
        temp.replace(args.output)
    except Exception:
        temp.unlink(missing_ok=True)
        raise

    total = sum(x["kept"] for x in reports.values())
    report_path = args.output.with_suffix(".report.json")
    report_path.write_text(json.dumps({
        "status": "format_checked_not_fact_checked",
        "total_kept": total,
        "sources": reports,
        "note": "Confirm dataset licenses and review answer quality before training."
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Merged {total} unique records -> {args.output}")
    print(f"Report -> {report_path}")
    print("This is data preparation only; no model weights were trained.")


if __name__ == "__main__":
    main()
