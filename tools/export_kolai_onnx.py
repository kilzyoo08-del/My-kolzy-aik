#!/usr/bin/env python3
"""Export a validated Kolai byte-level checkpoint to ONNX for Android CPU inference."""
from __future__ import annotations
import argparse
from pathlib import Path
import torch
from torch import nn

EXPECTED = {
    "width": 128, "heads": 4, "layers": 4, "context": 256,
    "dropout": 0.1, "vocab_size": 258, "tokenizer": "utf8_byte_v1",
}

class Attention(nn.Module):
    def __init__(self, width: int, heads: int):
        super().__init__()
        self.heads = heads
        self.qkv = nn.Linear(width, 3 * width)
        self.proj = nn.Linear(width, width)

    def forward(self, x):
        batch, seq, width = x.shape
        q, k, v = self.qkv(x).chunk(3, dim=-1)
        head_dim = width // self.heads
        q = q.view(batch, seq, self.heads, head_dim).transpose(1, 2)
        k = k.view(batch, seq, self.heads, head_dim).transpose(1, 2)
        v = v.view(batch, seq, self.heads, head_dim).transpose(1, 2)
        scores = (q @ k.transpose(-2, -1)) * (head_dim ** -0.5)
        mask = torch.tril(torch.ones((seq, seq), dtype=torch.bool, device=x.device))
        scores = scores.masked_fill(~mask, torch.finfo(scores.dtype).min)
        weights = torch.softmax(scores, dim=-1)
        out = weights @ v
        return self.proj(out.transpose(1, 2).contiguous().view(batch, seq, width))

class Block(nn.Module):
    def __init__(self, width: int, heads: int):
        super().__init__()
        self.ln1 = nn.LayerNorm(width)
        self.attn = Attention(width, heads)
        self.ln2 = nn.LayerNorm(width)
        self.mlp = nn.Sequential(
            nn.Linear(width, 4 * width), nn.GELU(),
            nn.Linear(4 * width, width), nn.Identity()
        )

    def forward(self, x):
        x = x + self.attn(self.ln1(x))
        return x + self.mlp(self.ln2(x))

class ExportLM(nn.Module):
    def __init__(self, cfg):
        super().__init__()
        width, heads, layers, context = (
            cfg["width"], cfg["heads"], cfg["layers"], cfg["context"]
        )
        self.token_embedding = nn.Embedding(258, width)
        self.position_embedding = nn.Embedding(context, width)
        self.blocks = nn.Sequential(*[Block(width, heads) for _ in range(layers)])
        self.final_norm = nn.LayerNorm(width)
        self.lm_head = nn.Linear(width, 258, bias=False)
        self.lm_head.weight = self.token_embedding.weight

    def forward(self, tokens):
        _, seq = tokens.shape
        pos = torch.arange(seq, device=tokens.device)
        x = self.token_embedding(tokens) + self.position_embedding(pos)[None, :, :]
        return self.lm_head(self.final_norm(self.blocks(x)))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", default="models/kolai_2m_v1/kolai_best.pt")
    parser.add_argument("--output", default="app/src/main/assets/models/kolai_2m_v1.onnx")
    args = parser.parse_args()
    try:
        import onnx
    except ImportError as exc:
        raise SystemExit("Install the free 'onnx' package in the export environment first.") from exc

    checkpoint = Path(args.checkpoint)
    output = Path(args.output)
    state = torch.load(checkpoint, map_location="cpu", weights_only=True)
    if state.get("config") != EXPECTED:
        raise ValueError("Checkpoint config mismatch; refusing export.")
    model = ExportLM(EXPECTED)
    model.load_state_dict(state["model"], strict=True)
    model.eval()
    output.parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.tensor([[256, 35, 36, 37]], dtype=torch.long)
    torch.onnx.export(
        model, dummy, str(output),
        input_names=["tokens"], output_names=["logits"],
        opset_version=17,
        dynamic_axes={"tokens": {1: "sequence"}, "logits": {1: "sequence"}},
        do_constant_folding=True,
        dynamo=False,
    )
    exported = onnx.load(str(output))
    onnx.checker.check_model(exported)
    print({
        "status": "ONNX_EXPORT_PASS",
        "output": str(output),
        "bytes": output.stat().st_size,
        "opset": 17,
        "dynamic_sequence": True,
        "tokenizer": EXPECTED["tokenizer"],
        "context_bytes": EXPECTED["context"],
        "checkpoint_step": state.get("step"),
    })

if __name__ == "__main__":
    main()
