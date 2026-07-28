#!/usr/bin/env bash
# Seed placeholder cover images via admin cover-upload API → LocalStack S3.
# Prerequisites: api-gateway :8080, LocalStack :4566, admin bootstrapped.
#
# Usage (from bookstore-platform/):  ./scripts/seed-covers.sh
set -euo pipefail

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USERNAME:-admin}"
ADMIN_PASS="${ADMIN_PASSWORD:-admin12345}"
WORKDIR="${TMPDIR:-/tmp}/bookstore-covers-seed"
mkdir -p "$WORKDIR"

echo "Logging in as ${ADMIN_USER} via ${GATEWAY}..."
TOKEN=$(curl -fsS -X POST "${GATEWAY}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

echo "Fetching catalog..."
curl -fsS "${GATEWAY}/api/books?size=50" -H "Authorization: Bearer ${TOKEN}" > "$WORKDIR/books.json"

python3 - <<PY
import json, textwrap
from pathlib import Path

workdir = Path("$WORKDIR")
books = json.loads((workdir / "books.json").read_text())["content"]
colors = ["#2c5f2d", "#1a3a5c", "#6b2d5c", "#8b4513", "#3d5a80", "#2f3e46", "#5c4d7a"]

try:
    from PIL import Image, ImageDraw, ImageFont
    use_pil = True
except ImportError:
    use_pil = False

# Tiny valid JPEG fallback (1x1) when Pillow is missing.
MINI_JPEG = bytes([
    0xFF,0xD8,0xFF,0xE0,0x00,0x10,0x4A,0x46,0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,
    0x00,0x01,0x00,0x00,0xFF,0xDB,0x00,0x43,0x00,0x08,0x06,0x06,0x07,0x06,0x05,0x08,
    0x07,0x07,0x07,0x09,0x09,0x08,0x0A,0x0C,0x14,0x0D,0x0C,0x0B,0x0B,0x0C,0x19,0x12,
    0x13,0x0F,0x14,0x1D,0x1A,0x1F,0x1E,0x1D,0x1A,0x1C,0x1C,0x20,0x24,0x2E,0x27,0x20,
    0x22,0x2C,0x23,0x1C,0x1C,0x28,0x37,0x29,0x2C,0x30,0x31,0x34,0x34,0x34,0x1F,0x27,
    0x39,0x3D,0x38,0x32,0x3C,0x2E,0x33,0x34,0x32,0xFF,0xC0,0x00,0x0B,0x08,0x00,0x01,
    0x00,0x01,0x01,0x01,0x11,0x00,0xFF,0xC4,0x00,0x14,0x00,0x01,0x00,0x00,0x00,0x00,
    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x08,0xFF,0xC4,0x00,0x14,
    0x10,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
    0x00,0x00,0xFF,0xDA,0x00,0x08,0x01,0x01,0x00,0x00,0x3F,0x00,0x7F,0xFF,0xD9
])

ids = []
for i, b in enumerate(books):
    bid = b["id"]
    title = b.get("title") or f"Book {bid}"
    author = b.get("authorName") or ""
    path = workdir / f"cover-{bid}.jpg"
    if use_pil:
        img = Image.new("RGB", (240, 360), colors[i % len(colors)])
        draw = ImageDraw.Draw(img)
        font = ImageFont.load_default()
        y = 80
        for line in textwrap.wrap(title, width=18):
            draw.text((20, y), line, fill="white", font=font)
            y += 14
        draw.text((20, 300), author[:28], fill="#dddddd", font=font)
        img.save(path, quality=85)
    else:
        path.write_bytes(MINI_JPEG)
    ids.append(str(bid))
    print(f"prepared cover for book {bid}: {title}")

(workdir / "book-ids.txt").write_text("\n".join(ids) + "\n")
if not use_pil:
    print("(install pillow for titled covers: pip install pillow)")
PY

while IFS= read -r BOOK_ID; do
  [ -z "$BOOK_ID" ] && continue
  FILE="$WORKDIR/cover-${BOOK_ID}.jpg"
  echo "Uploading cover for book ${BOOK_ID}..."
  UPLOAD=$(curl -fsS -X POST "${GATEWAY}/api/books/${BOOK_ID}/cover?contentType=image/jpeg" \
    -H "Authorization: Bearer ${TOKEN}")
  # Normalize virtual-hosted LocalStack URLs to path-style on localhost for host-side curl.
  UPLOAD_URL=$(echo "$UPLOAD" | python3 -c '
import json, sys
from urllib.parse import urlparse, urlunparse
url = json.load(sys.stdin)["uploadUrl"]
u = urlparse(url)
host = u.hostname or ""
port = u.port or 4566
if host.endswith(".localstack") or (host != "localhost" and host.endswith(".localhost")):
    bucket = host.split(".")[0]
    path = "/" + bucket + u.path
    print(urlunparse(u._replace(netloc=f"localhost:{port}", path=path)))
else:
    print(url.replace("://localstack:", "://localhost:").replace(".localstack:", ".localhost:"))
')
  HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT "$UPLOAD_URL" \
    -H 'Content-Type: image/jpeg' --data-binary @"$FILE")
  COVER=$(curl -fsS "${GATEWAY}/api/books/${BOOK_ID}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin).get("coverUrl") or "")')
  echo "  put=${HTTP_CODE} coverUrl=${COVER}"
done < "$WORKDIR/book-ids.txt"

echo
echo "Done. Refresh the Angular home page to see thumbnails."
