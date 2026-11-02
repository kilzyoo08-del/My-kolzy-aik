"""Train Kolai in small cumulative stages with a separate checkpoint per stage.

Example:
  python ml/kolzailm/train_staged.py --data ml/kolzailm/data/instructions.clean.jsonl
The input must already be reviewed/cleaned JSONL. This script does not verify facts.
"""
import argparse
import json
import random
import subprocess
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", required=True)
    parser.add_argument("--steps-per-stage", type=int, default=250)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--context", type=int, default=256)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--work-dir", default="ml/kolzailm/staged")
    args = parser.parse_args()

    source = Path(args.data)
    rows = []
    seen = set()
    for line_no, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        item = json.loads(line)
        if not isinstance(item, dict) or not isinstance(item.get("instruction"), str) or not isinstance(item.get("output"), str):
            raise SystemExit(f"Invalid instruction/output at {source}:{line_no}")
        if not item["instruction"].strip() or not item["output"].strip():
            raise SystemExit(f"Empty instruction/output at {source}:{line_no}")
        key = (item["instruction"].strip(), str(item.get("input", "")).strip(), item["output"].strip())
        if key in seen:
            continue
        seen.add(key)
        rows.append({"instruction": key[0], "input": key[1], "output": key[2]})

    if len(rows) < 20:
        raise SystemExit(f"Need at least 20 unique examples; found {len(rows)}")
    random.Random(args.seed).shuffle(rows)

    work = Path(args.work_dir)
    work.mkdir(parents=True, exist_ok=True)
    # Each stage expands the cumulative training pool: 10%, 25%, 50%, then 100%.
    fractions = (0.10, 0.25, 0.50, 1.0)
    previous_checkpoint = ""
    trainer = Path(__file__).with_name("train.py")

    for index, fraction in enumerate(fractions, 1):
        count = max(20, int(len(rows) * fraction))
        stage_data = work / f"stage_{index}.jsonl"
        stage_checkpoint = work / f"kolai_stage_{index}.pt"
        with stage_data.open("w", encoding="utf-8", newline="\n") as stream:
            for row in rows[:count]:
                stream.write(json.dumps(row, ensure_ascii=False) + "\n")

        command = [
            sys.executable, str(trainer),
            "--data", str(stage_data),
            "--steps", str(args.steps_per_stage),
            "--batch-size", str(args.batch_size),
            "--context", str(args.context),
            "--device", args.device,
            "--seed", str(args.seed + index),
            "--out", str(stage_checkpoint),
        ]
        if previous_checkpoint:
            command += ["--init-checkpoint", previous_checkpoint]

        print(f"\n=== Stage {index}/4: {count} examples ===", flush=True)
        subprocess.run(command, check=True)
        if not stage_checkpoint.is_file() or stage_checkpoint.stat().st_size == 0:
            raise SystemExit(f"Stage {index} did not produce a checkpoint; previous checkpoint is preserved")
        previous_checkpoint = str(stage_checkpoint)

    final_path = work / "kolai_staged_final.pt"
    final_path.write_bytes(Path(previous_checkpoint).read_bytes())
    print(f"\nAll stages completed. Final checkpoint: {final_path}")


if __name__ == "__main__":
    main()
