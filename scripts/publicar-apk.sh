#!/usr/bin/env bash
# Publica un APK compilado en la carpeta /apk/ de la instancia y registra la
# versión en git (manifiesto + tag). El binario NO se versiona en el repo
# público: se sirve desde la instancia.
#
# Uso:
#   APK_PUBLIC_DIR=/ruta/a/apk scripts/publicar-apk.sh <apk> <versionName> <versionCode>
# Ejemplo:
#   APK_PUBLIC_DIR=/ruta/a/apk \
#     scripts/publicar-apk.sh app/build/outputs/apk/release/app-release.apk 1.14 16

set -euo pipefail

if [ "$#" -lt 3 ]; then
  echo "Uso: APK_PUBLIC_DIR=/ruta/a/apk $0 <apk> <versionName> <versionCode>" >&2
  exit 2
fi

APK_SRC="$1"
VERSION="$2"
CODE="$3"

if [ ! -f "$APK_SRC" ]; then
  echo "No existe el APK: $APK_SRC" >&2
  exit 1
fi
: "${APK_PUBLIC_DIR:?Define APK_PUBLIC_DIR (carpeta pública /apk/ de la instancia)}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$APK_PUBLIC_DIR"
FILE="espotifai-v${CODE}-${VERSION}.apk"
MANIFEST="$ROOT/apk/versions.json"

mkdir -p "$DEST" "$ROOT/apk"
cp -f "$APK_SRC" "$DEST/$FILE"
cp -f "$APK_SRC" "$DEST/espotifai.apk"

SHA="$(sha256sum "$DEST/$FILE" | awk '{print $1}')"
SIZE="$(stat -c '%s' "$DEST/$FILE")"
DATE="$(date -Iseconds)"

[ -f "$MANIFEST" ] || printf '[]\n' > "$MANIFEST"
jq --arg version "$VERSION" --argjson code "$CODE" --arg file "$FILE" \
   --arg sha "$SHA" --argjson size "$SIZE" --arg date "$DATE" \
   '[{version:$version,versionCode:$code,file:$file,sha256:$sha,size:$size,date:$date}]
    + [.[] | select(.versionCode != $code)]' \
   "$MANIFEST" > "$MANIFEST.tmp"
mv "$MANIFEST.tmp" "$MANIFEST"

cd "$ROOT"
git add apk/versions.json
if ! git diff --cached --quiet; then
  git commit -m "Publica APK v${VERSION}"
fi
if ! git rev-parse -q --verify "refs/tags/v${VERSION}" >/dev/null; then
  git tag -a "v${VERSION}" -m "APK v${VERSION} (versionCode ${CODE})"
fi

if git remote get-url origin >/dev/null 2>&1; then
  if ! git push origin main --tags; then
    echo "Aviso: no se pudo hacer push a origin (repo no creado o sin permisos)." >&2
  fi
fi

echo "APK ${VERSION} (versionCode ${CODE}) publicado en: $DEST/$FILE"
echo "Manifiesto actualizado: apk/versions.json (commit/tag local si hay cambios)"
