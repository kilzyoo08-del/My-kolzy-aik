"""Tiny decoder-only Transformer language model for KolzaiLM (trained from random weights)."""
import math
import torch
from torch import nn
from torch.nn import functional as F

VOCAB_SIZE = 258  # byte values 0..255, BOS=256, EOS=257

class CausalSelfAttention(nn.Module):
    def __init__(self, width: int, heads: int, dropout: float):
        super().__init__()
        if width % heads:
            raise ValueError("width must be divisible by heads")
        self.heads = heads
        self.qkv = nn.Linear(width, 3 * width)
        self.proj = nn.Linear(width, width)
        self.attn_dropout = nn.Dropout(dropout)
        self.resid_dropout = nn.Dropout(dropout)

    def forward(self, x):
        batch, length, width = x.shape
        q, k, v = self.qkv(x).chunk(3, dim=-1)
        head_dim = width // self.heads
        q = q.view(batch, length, self.heads, head_dim).transpose(1, 2)
        k = k.view(batch, length, self.heads, head_dim).transpose(1, 2)
        v = v.view(batch, length, self.heads, head_dim).transpose(1, 2)
        mask = torch.tril(torch.ones(length, length, device=x.device, dtype=torch.bool))
        scores = (q @ k.transpose(-2, -1)) / math.sqrt(head_dim)
        scores = scores.masked_fill(~mask, torch.finfo(scores.dtype).min)
        weights = self.attn_dropout(F.softmax(scores, dim=-1))
        out = (weights @ v).transpose(1, 2).contiguous().view(batch, length, width)
        return self.resid_dropout(self.proj(out))

class Block(nn.Module):
    def __init__(self, width: int, heads: int, dropout: float):
        super().__init__()
        self.ln1 = nn.LayerNorm(width)
        self.attn = CausalSelfAttention(width, heads, dropout)
        self.ln2 = nn.LayerNorm(width)
        self.mlp = nn.Sequential(
            nn.Linear(width, 4 * width), nn.GELU(),
            nn.Linear(4 * width, width), nn.Dropout(dropout),
        )

    def forward(self, x):
        x = x + self.attn(self.ln1(x))
        return x + self.mlp(self.ln2(x))

class KolzaiLM(nn.Module):
    def __init__(self, width=128, heads=4, layers=4, context=128, dropout=0.1):
        super().__init__()
        self.config = dict(width=width, heads=heads, layers=layers, context=context, dropout=dropout)
        self.token_embedding = nn.Embedding(VOCAB_SIZE, width)
        self.position_embedding = nn.Embedding(context, width)
        self.blocks = nn.Sequential(*[Block(width, heads, dropout) for _ in range(layers)])
        self.final_norm = nn.LayerNorm(width)
        self.lm_head = nn.Linear(width, VOCAB_SIZE, bias=False)
        self.lm_head.weight = self.token_embedding.weight
        self.apply(self._init_weights)

    @staticmethod
    def _init_weights(module):
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
            if module.bias is not None:
                nn.init.zeros_(module.bias)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)

    def forward(self, tokens, targets=None):
        batch, length = tokens.shape
        if length > self.config["context"]:
            raise ValueError("sequence exceeds configured context")
        positions = torch.arange(length, device=tokens.device)
        x = self.token_embedding(tokens) + self.position_embedding(positions)[None, :, :]
        logits = self.lm_head(self.final_norm(self.blocks(x)))
        loss = None
        if targets is not None:
            loss = F.cross_entropy(logits.reshape(-1, VOCAB_SIZE), targets.reshape(-1), ignore_index=-100)
        return logits, loss

def encode(text: str):
    return [256] + list(text.encode("utf-8")) + [257]

def decode(tokens):
    raw = bytes(int(t) for t in tokens if 0 <= int(t) <= 255)
    return raw.decode("utf-8", errors="replace")
