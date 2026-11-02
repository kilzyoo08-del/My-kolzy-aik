"""Generate text from a trained KolzaiLM checkpoint."""
import argparse
import torch
from model import KolzaiLM, encode, decode

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--checkpoint", default="ml/kolzailm/checkpoints/kolzailm.pt")
    p.add_argument("--prompt", default="Привет! ")
    p.add_argument("--new-bytes", type=int, default=300)
    p.add_argument("--temperature", type=float, default=0.8)
    p.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    args = p.parse_args()
    if args.temperature <= 0:
        raise SystemExit("--temperature must be > 0")
    device = torch.device(args.device)
    checkpoint = torch.load(args.checkpoint, map_location=device)
    model = KolzaiLM(**checkpoint["config"]).to(device)
    model.load_state_dict(checkpoint["model"])
    model.eval()
    tokens = torch.tensor([encode(args.prompt)], dtype=torch.long, device=device)
    with torch.no_grad():
        for _ in range(args.new_bytes):
            window = tokens[:, -model.config["context"]:]
            logits, _ = model(window)
            probs = torch.softmax(logits[:, -1, :] / args.temperature, dim=-1)
            next_token = torch.multinomial(probs, num_samples=1)
            tokens = torch.cat((tokens, next_token), dim=1)
            if next_token.item() == 257:
                break
    print(decode(tokens[0].tolist()))

if __name__ == "__main__":
    main()
