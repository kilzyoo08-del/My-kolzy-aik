"""Instruction fine-tuning for Kolai: compute next-byte loss on answers only."""
import argparse
import json
import random
from pathlib import Path

import torch
from model import KolzaiLM, VOCAB_SIZE


def load_examples(path, context):
    """Return (input_ids, target_ids) pairs; prompt targets are masked with -100."""
    examples = []
    for line_no, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
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
            raise SystemExit(f"Line {line_no}: instruction and output must be non-empty")

        prompt = "Пользователь: " + instruction
        if extra:
            prompt += "\nВвод: " + extra
        prompt += "\nKolai: "
        prompt_ids = list(prompt.encode("utf-8"))
        answer_ids = list(answer.encode("utf-8")) + [257]  # EOS is part of the answer.

        # Keep the answer ending and the most recent prompt context within the model window.
        answer_ids = answer_ids[-context:]
        prompt_budget = max(0, context - len(answer_ids))
        prompt_ids = prompt_ids[-prompt_budget:] if prompt_budget else []
        sequence = [256] + prompt_ids + answer_ids
        if len(sequence) < 2:
            continue
        inputs = sequence[:-1]
        targets = sequence[1:]
        answer_start = 1 + len(prompt_ids)
        masked_targets = [-100] * len(targets)
        for i in range(len(targets)):
            # Target token position in sequence is i+1.
            if i + 1 >= answer_start:
                masked_targets[i] = targets[i]
        examples.append((inputs, masked_targets))
    if len(examples) < 2:
        raise SystemExit(f"Need at least 2 valid instruction examples; found {len(examples)}")
    return examples


def make_batch(examples, indices, device):
    batch = [examples[i] for i in indices]
    length = max(len(x) for x, _ in batch)
    x = torch.zeros((len(batch), length), dtype=torch.long)
    y = torch.full((len(batch), length), -100, dtype=torch.long)
    for row, (tokens, labels) in enumerate(batch):
        x[row, :len(tokens)] = torch.tensor(tokens, dtype=torch.long)
        y[row, :len(labels)] = torch.tensor(labels, dtype=torch.long)
    return x.to(device), y.to(device)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--data", default="ml/kolzailm/data/instructions.jsonl")
    p.add_argument("--steps", type=int, default=1000)
    p.add_argument("--batch-size", type=int, default=4)
    p.add_argument("--context", type=int, default=256)
    p.add_argument("--width", type=int, default=128)
    p.add_argument("--heads", type=int, default=4)
    p.add_argument("--layers", type=int, default=4)
    p.add_argument("--lr", type=float, default=3e-4)
    p.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--eval-every", type=int, default=100)
    p.add_argument("--out", default="ml/kolzailm/checkpoints/kolai-instruct.pt")
    args = p.parse_args()

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    device = torch.device(args.device)
    examples = load_examples(args.data, args.context)
    random.shuffle(examples)
    split = max(1, int(len(examples) * 0.9))
    train_examples = examples[:split]
    val_examples = examples[split:] or examples[:]
    print(f"Loaded {len(examples)} examples; train={len(train_examples)}, validation={len(val_examples)}")

    model = KolzaiLM(args.width, args.heads, args.layers, args.context).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)

    @torch.no_grad()
    def evaluate():
        model.eval()
        order = list(range(len(val_examples)))
        losses = []
        for start in range(0, len(order), args.batch_size):
            ids = order[start:start + args.batch_size]
            x, y = make_batch(val_examples, ids, device)
            _, loss = model(x, y)
            losses.append(loss.item())
        model.train()
        return sum(losses) / len(losses)

    model.train()
    for step in range(1, args.steps + 1):
        ids = [random.randrange(len(train_examples)) for _ in range(args.batch_size)]
        x, y = make_batch(train_examples, ids, device)
        _, loss = model(x, y)
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        if step == 1 or step % args.eval_every == 0 or step == args.steps:
            print(f"step {step}/{args.steps} train_loss={loss.item():.4f} val_loss={evaluate():.4f}", flush=True)

    output = Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    torch.save({
        "model_name": "Kolai",
        "training_type": "answer_only_instruction_finetuning",
        "config": model.config,
        "model": model.state_dict(),
        "vocab_size": VOCAB_SIZE,
        "training_examples": len(examples),
        "seed": args.seed,
    }, output)
    print(f"Saved checkpoint: {output.resolve()}")


if __name__ == "__main__":
    main()
