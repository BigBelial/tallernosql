#!/usr/bin/env bash
# Genera el .zip de entrega en el Escritorio: ~/Desktop/nosqltallerJuanJoseOspinaSanchez.zip
# Solo incluye los archivos del proyecto (respeta el .gitignore, así que no van build/, .gradle/, .git/
# ni archivos del sistema como .DS_Store). Al descomprimirlo, todo queda dentro de una carpeta con el mismo nombre.
# Uso: ./scripts/generar-zip.sh
set -euo pipefail

NOMBRE="nosqltallerJuanJoseOspinaSanchez"
DESTINO="$HOME/Desktop/$NOMBRE.zip"
PROYECTO="$(cd "$(dirname "$0")/.." && pwd)"
TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$TEMPORAL"' EXIT

# Se copian los archivos del proyecto a una carpeta temporal con el nombre de la entrega
mkdir "$TEMPORAL/$NOMBRE"
cd "$PROYECTO"
git ls-files -z --cached --others --exclude-standard | tar --null -T - -cf - | tar -xf - -C "$TEMPORAL/$NOMBRE"

# Se crea el .zip (si ya existía uno viejo, se reemplaza)
rm -f "$DESTINO"
(cd "$TEMPORAL" && zip -rqX "$DESTINO" "$NOMBRE")

echo "Listo: $DESTINO"
unzip -l "$DESTINO" | tail -1
