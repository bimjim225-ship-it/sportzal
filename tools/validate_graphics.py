"""Verify that all bundled native graphics match the prepared asset package."""
import hashlib
import json
import re
import struct
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
expected = json.loads((ROOT / "docs/graphics/native-sha256.json").read_text())
assert len(expected) == 49
counts = {".webp": 0, ".xml": 0}
for relative, digest in expected.items():
    path = ROOT / relative
    assert re.fullmatch(r"sz_[a-z0-9_]+", path.stem), path
    data = path.read_bytes()
    assert hashlib.sha256(data).hexdigest() == digest, f"Asset changed: {relative}"
    if path.suffix == ".xml":
        assert ET.fromstring(data).tag == "vector", path
    else:
        assert data[:4] == b"RIFF" and data[8:12] == b"WEBP", path
        assert struct.unpack("<I", data[4:8])[0] + 8 == len(data), path
    counts[path.suffix] += 1
assert counts == {".webp": 25, ".xml": 24}, counts
print("PASS: 25 WebP and 24 VectorDrawable files match the graphics package")
