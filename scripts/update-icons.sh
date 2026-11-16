#!/bin/sh
# Make wpsld's icons again from ui/src/icons/wpsld.svg:
#
#   scripts/update-icons.sh
#
# Writes, for the app images' launchers (jpackage, see ui/build.gradle),
# ui/src/icons/wpsld.png (Linux, 512 px), wpsld.ico (Windows, 16 to 256 px)
# and wpsld.icns (macOS, from a 1024 px rendering); and, for the windows and
# the About box, ui/src/main/resources/no/s11/wpsld/ui/icons/wpsld-<size>.png.
#
# Needs a Python with cairosvg and Pillow, which the project's environment
# doesn't have, e.g. a temporary one:
#
#   conda create -p /tmp/icons -c conda-forge python cairosvg pillow
#   PYTHON=/tmp/icons/bin/python scripts/update-icons.sh
set -e
cd "$(dirname "$0")/.."
"${PYTHON:-python3}" - <<'PY'
import io
import cairosvg
from PIL import Image

SVG = 'ui/src/icons/wpsld.svg'
WINDOW_SIZES = (16, 24, 32, 48, 64, 128, 256)


def render(size):
    return Image.open(io.BytesIO(cairosvg.svg2png(url=SVG, output_width=size, output_height=size))).convert('RGBA')


for size in WINDOW_SIZES:
    render(size).save(f'ui/src/main/resources/no/s11/wpsld/ui/icons/wpsld-{size}.png', optimize=True)
render(512).save('ui/src/icons/wpsld.png', optimize=True)
# Each size rendered from the SVG, not scaled down from the largest
icons = [render(size) for size in reversed(WINDOW_SIZES)]
icons[0].save('ui/src/icons/wpsld.ico', sizes=[i.size for i in icons], append_images=icons[1:])
render(1024).save('ui/src/icons/wpsld.icns')
PY
echo "Icons written; check them before committing"
