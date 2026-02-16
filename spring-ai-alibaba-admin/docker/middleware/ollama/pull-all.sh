#!/bin/bash
# Pull all models listed in models.conf via Ollama API
# Usage: ./pull-all.sh [OLLAMA_URL]
#   Default OLLAMA_URL: http://localhost:11434

OLLAMA_URL="${1:-http://localhost:11434}"
MODELS_CONF="$(dirname "$0")/models.conf"

if [ ! -f "$MODELS_CONF" ]; then
  echo "ERROR: $MODELS_CONF not found"
  exit 1
fi

echo "=== Pulling models from $MODELS_CONF ==="
echo "    Ollama URL: $OLLAMA_URL"
echo

# Get already-pulled models
EXISTING=$(curl -s "$OLLAMA_URL/api/tags" | python3 -c "
import sys,json
try:
  data = json.load(sys.stdin)
  for m in data.get('models',[]):
    name = m['name']
    # Normalize: strip :latest
    if name.endswith(':latest'):
      name = name[:-7]
    print(name)
except: pass
" 2>/dev/null)

while IFS= read -r MODEL; do
  # Skip comments and blank lines
  [[ "$MODEL" =~ ^[[:space:]]*# ]] && continue
  [[ -z "${MODEL// /}" ]] && continue

  # Check if already present
  if echo "$EXISTING" | grep -qxF "$MODEL"; then
    echo "[SKIP] $MODEL — already present"
    continue
  fi

  echo "[PULL] $MODEL ..."
  START=$(date +%s)

  # Pull via API (non-streaming, waits for completion)
  RESULT=$(curl -s -X POST "$OLLAMA_URL/api/pull" \
    -d "{\"name\":\"$MODEL\",\"stream\":false}" \
    --max-time 1800 2>&1)

  END=$(date +%s)
  ELAPSED=$((END - START))

  if echo "$RESULT" | grep -q '"status":"success"'; then
    echo "[DONE] $MODEL — ${ELAPSED}s"
  else
    echo "[FAIL] $MODEL — ${ELAPSED}s"
    echo "       $RESULT" | head -3
  fi
done < "$MODELS_CONF"

echo
echo "=== Removing models NOT in $MODELS_CONF ==="

# Build a list of wanted model names (normalized, no :latest suffix)
WANTED=$(grep -v '^ *#' "$MODELS_CONF" | grep -v '^ *$' | sed 's/[[:space:]]//g')

# Get installed models and check each against the wanted list
curl -s "$OLLAMA_URL/api/tags" | python3 -c "
import sys,json
data = json.load(sys.stdin)
for m in data.get('models',[]):
  name = m['name']
  if name.endswith(':latest'):
    name = name[:-7]
  print(name)
" 2>/dev/null | while IFS= read -r INSTALLED; do
  KEEP=false
  echo "$WANTED" | while IFS= read -r W; do
    NORM_W="$W"
    # Strip :latest from wanted entry too
    case "$NORM_W" in *:latest) NORM_W="${NORM_W%:latest}" ;; esac
    if [ "$INSTALLED" = "$NORM_W" ]; then
      touch /tmp/ollama_keep_match
    fi
  done

  if [ -f /tmp/ollama_keep_match ]; then
    rm -f /tmp/ollama_keep_match
    echo "[KEEP]   $INSTALLED"
  else
    echo "[REMOVE] $INSTALLED — not in models.conf"
    curl -s -X DELETE "$OLLAMA_URL/api/delete" \
      -d "{\"name\":\"$INSTALLED\"}" >/dev/null 2>&1 || true
  fi
done

echo
echo "=== Final model list ==="
curl -s "$OLLAMA_URL/api/tags" | python3 -c "
import sys,json
data = json.load(sys.stdin)
for m in data.get('models',[]):
  size_gb = m.get('size',0) / 1073741824
  print(f\"  {m['name']:<30s} {size_gb:.1f} GB\")
"
