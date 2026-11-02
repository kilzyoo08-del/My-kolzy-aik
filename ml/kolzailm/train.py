"""Train KolzaiLM from scratch on a UTF-8 text file."""
import argparse
import math
import random
from pathlib import Path
import torch
from model import KolzaiLM, encode

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--data", default="ml/kolzailm/data/train.txt")
    p.add_argument("--steps", type=int, default=3000)
    p.add_argument("--batch-size", type=int, default=8)
    p.add_argument("--context", type=int, default=128)
    p.add_argument("--width", type=int, default=128)
    p.add_argument("--heads", type=int, default=4)
    p.add_argument("--layers", type=int, default=4)
    p.add_argument("--lr", type=float, default=3e-4)
    p.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--eval-every", type=int, default=200)
    p.add_argument("--out", default="ml/kolzailm/checkpoints/kolzailm.pt")
    args = p.parse_args()

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    device = torch.device(args.device)
    text = Path(args.data).read_text(encoding="utf-8")
    ids = torch.tensor([token for line in text.splitlines() if line.strip() for token in encode(line + "\n")], dtype=torch.long)
    if len(ids) < args.context * 4:
        raise SystemExit(f"Corpus too small: {len(ids)} tokens. Add more text (at least {args.context * 4} bytes/tokens).")
    split = int(len(ids) * 0.9)
    train_ids, val_ids = ids[:split], ids[split:]
    if len(val_ids) <= args.context + 1:
        train_ids, val_ids = ids, ids
    def batch(source):
        starts = torch.randint(0, len(source) - args.context - 1, (args.batch_size,))
        x = torch.stack([source[s:s + args.context] for s in starts]).to(device)
        y = torch.stack([source[s + 1:s + args.context + 1] for s in starts]).to(device)
        return x, y
    model = KolzaiLM(args.width, args.heads, args.layers, args.context).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)
    @torch.no_grad()
    def evaluate(source, rounds=20):
        model.eval()
        values = [model(*batch(source))[1].item() for _ in range(rounds)]
        model.train()
        return sum(values) / len(values)
    model.train()
    for step in range(1, args.steps + 1):
        x, y = batch(train_ids)
        _, loss = model(x, y)
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        if step == 1 or step % args.eval_every == 0 or step == args.steps:
            val_loss = evaluate(val_ids)
            print(f"step {step}/{args.steps} train_loss={loss.item():.4f} val_loss={val_loss:.4f}", flush=True)
    output = Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"config": model.config, "model": model.state_dict(), "vocab_size": 258}, output)
    print(f"Saved checkpoint: {output.resolve()}")

if __name__ == "__main__":
    main()
