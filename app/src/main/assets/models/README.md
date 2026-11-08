# Kolai model asset

The Android client expects `kolai_2m_v1.onnx` in this folder.

Do not add a placeholder or randomly initialized model. Copy the exported ONNX file here only after:
1. the new Kolai training run completes successfully;
2. strict checkpoint loading succeeds;
3. the ONNX graph passes `onnx.checker`;
4. ONNX Runtime CPU output matches PyTorch logits within tolerance;
5. Russian/English generation smoke tests and the sealed test report are reviewed.

Tokenizer: UTF-8 byte IDs 0–255, BOS=256, EOS=257. Context: 256 bytes.
