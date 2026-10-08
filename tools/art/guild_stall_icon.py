"""
Render the native 16px stall sprite using the existing Enthusia palette.

Run from any directory with Python and Pillow; no smoothing or resampling.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / 'resourcepack/enthusia-icons/Nexo/pack/assets/lumaguilds/textures/enthusia/stall.png'
PALETTE = {
    '.': (0, 0, 0, 0),
    'O': (42, 18, 8, 255),  # shared icon outline
    'R': (216, 58, 48, 255),  # home roof red
    'r': (168, 30, 40, 255),
    'G': (247, 196, 49, 255),  # bank gold
    'g': (226, 145, 4, 255),
    'H': (255, 245, 223, 255),  # common highlight
    'W': (184, 128, 74, 255),  # actions/home wood
    'h': (154, 96, 52, 255),
    'w': (122, 68, 34, 255),
}
PIXELS = (
    '................',
    '....OOOOOOOO....',
    '...ORRGGRRGGO...',
    '..ORRRGGGRRRGGO.',
    '.OOOOOOOOOOOOOO.',
    '.OrrrgggrrrgggO.',
    '..OW........WO..',
    '..OW........WO..',
    '..OW..GG....WO..',
    '..OW.GHGG...WO..',
    '..OOOOOOOOOOOO..',
    '..OHhhhhhhhhWO..',
    '..OWwwwwwwwwWO..',
    '..OWwwwwwwwwWO..',
    '..OOOOOOOOOOOO..',
    '................',
)


def main():
    if len(PIXELS) != 16 or any(len(row) != 16 for row in PIXELS):
        raise ValueError("Stall sprite must be exactly 16 by 16 pixels")
    image = Image.new('RGBA', (16, 16))
    image.putdata([PALETTE[pixel] for row in PIXELS for pixel in row])
    image.save(TEXTURE)
    bedrock = ROOT / 'resourcepack/enthusia-icons/Bedrock/textures/ui/stall.png'
    bedrock.parent.mkdir(parents=True, exist_ok=True)
    image.save(bedrock)
    print(TEXTURE)


if __name__ == '__main__':
    main()
