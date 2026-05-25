#!/usr/bin/env python3

import os
from io import BytesIO

import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT_DIR = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
ICONS_DIR = os.path.join(ROOT_DIR, 'icons')
BASE_SVG_PATH = os.path.join(ICONS_DIR, 'spoolman_base.svg')
APP_ICON_PATH = os.path.join(ICONS_DIR, 'AppIcon.png')


def rasterize_base(size):
    png_bytes = cairosvg.svg2png(url=BASE_SVG_PATH, output_width=size, output_height=size)
    return Image.open(BytesIO(png_bytes)).convert('RGBA')


def draw_rfid_accent(image):
    w, h = image.size
    draw = ImageDraw.Draw(image, 'RGBA')

    lines = ['RFID', 'LINK']
    max_w = int(w * 0.8)
    max_total_h = int(h * 0.72)

    try:
        font_size = int(w * 0.42)
        while font_size > 12:
            font = ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf', font_size)
            b0 = draw.textbbox((0, 0), lines[0], font=font)
            b1 = draw.textbbox((0, 0), lines[1], font=font)
            w0, h0 = b0[2] - b0[0], b0[3] - b0[1]
            w1, h1 = b1[2] - b1[0], b1[3] - b1[1]
            gap = int(font_size * 0.10)
            total_h = h0 + h1 + gap
            if max(w0, w1) <= max_w and total_h <= max_total_h:
                break
            font_size -= 2
    except OSError:
        font = ImageFont.load_default()
        b0 = draw.textbbox((0, 0), lines[0], font=font)
        b1 = draw.textbbox((0, 0), lines[1], font=font)
        w0, h0 = b0[2] - b0[0], b0[3] - b0[1]
        w1, h1 = b1[2] - b1[0], b1[3] - b1[1]
        gap = int(h * 0.01)

    total_h = h0 + h1 + gap
    top = (h - total_h) // 2

    x0 = (w - w0) // 2 - b0[0]
    x1 = (w - w1) // 2 - b1[0]
    y0 = top - b0[1]
    y1 = top + h0 + gap - b1[1]

    shadow = (10, 16, 30, 210)
    dx = int(w * 0.004)
    dy = int(w * 0.004)

    draw.text((x0 + dx, y0 + dy), lines[0], font=font, fill=shadow)
    draw.text((x1 + dx, y1 + dy), lines[1], font=font, fill=shadow)

    fill = (242, 248, 255, 255)
    stroke = (70, 126, 198, 255)
    sw = max(1, int(w * 0.003))

    draw.text((x0, y0), lines[0], font=font, fill=fill, stroke_width=sw, stroke_fill=stroke)
    draw.text((x1, y1), lines[1], font=font, fill=fill, stroke_width=sw, stroke_fill=stroke)

    return image


def apply_round_mask(image):
    size = image.size[0]
    radius = int(size * 0.18)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(image, mask=mask)
    return out


def main():
    icon = rasterize_base(1024)
    icon = draw_rfid_accent(icon)
    icon = apply_round_mask(icon)

    icon.convert('RGBA').save(APP_ICON_PATH)

    print(f'Generated {APP_ICON_PATH}')
    print('Updated Android launcher assets')
    print('Updated iOS AppIcon asset')
    print('Updated web icon assets')


if __name__ == '__main__':
    main()
