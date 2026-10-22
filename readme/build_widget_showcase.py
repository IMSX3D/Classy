"""Remove launcher wallpaper outside real widget outlines; rebuild README sheets.

Input is the eight explicitly named widget crops in media/v0.1.1.
Only alpha and the outer transparent margin change; widget RGB is preserved.
Run after build_showcase.py to keep both workflows consistent.
Requires Pillow, numpy and opencv-python.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cv2
import numpy as np

ROOT = Path(__file__).parent / 'media' / 'v0.1.1'
NAMES = ['widget-small', 'widget-today', 'widget-two-day', 'widget-week',
         'tablet-widget-small', 'tablet-widget-today',
         'tablet-widget-two-day', 'tablet-widget-week']


def isolate(path):
    image = Image.open(path).convert('RGBA')
    # Already processed crops keep their exact alpha; this is idempotent.
    if image.getchannel('A').getextrema()[0] < 255:
        return
    rgb = np.asarray(image)[:, :, :3]
    level = 255 if path.name.startswith('tablet-') else 219
    delta = np.abs(rgb.astype(np.int16) - level).max(axis=2)
    core = np.uint8(delta <= 4) * 255
    contours, _ = cv2.findContours(core, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    contour = max(contours, key=cv2.contourArea)
    assert cv2.contourArea(contour) > image.width * image.height * .65, path
    alpha = np.zeros(core.shape, dtype=np.uint8)
    cv2.drawContours(alpha, [contour], -1, 255, cv2.FILLED)
    # Slight inset removes wallpaper-contaminated antialiasing at the old edge.
    alpha = cv2.erode(alpha, np.ones((3, 3), np.uint8), borderType=cv2.BORDER_CONSTANT, borderValue=0)
    mask = Image.fromarray(alpha).filter(ImageFilter.GaussianBlur(.5))
    image.putalpha(mask)
    image.crop(mask.getbbox()).save(path, optimize=True)


def sheet(name, title, subtitle, items, height):
    canvas = Image.new('RGB', (1600, height), '#edf1ee')
    draw = ImageDraw.Draw(canvas)
    font = lambda size: ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', size)
    draw.text((64, 46), title, font=font(50), fill='#172c2b')
    draw.text((64, 125), subtitle, font=font(27), fill='#425b56')
    for filename, label, x, y, width in items:
        image = Image.open(ROOT / (filename + '.png')).convert('RGBA')
        image = image.resize((width, round(image.height * width / image.width)), Image.Resampling.LANCZOS)
        assert y + image.height <= height - 30
        draw.text((x, y - 50), label, font=font(29), fill='#172c2b')
        canvas.paste(image, (x, y), image)
    canvas.save(ROOT / (name + '.jpg'), quality=95, subsampling=0, optimize=True)


def main():
    for name in NAMES:
        isolate(ROOT / (name + '.png'))
    sheet('widgets', '不用打开 App，也能心中有数。',
          '四种桌面小组件 · vivo 手机实际桌面截图', [
              ('widget-small', '今日·小', 214, 245, 400),
              ('widget-today', '今日', 64, 865, 700),
              ('widget-two-day', '两日', 840, 245, 696),
              ('widget-week', '本周', 840, 675, 696)], 1460)
    sheet('tablet-widgets', '把课程，放在平板桌面。',
          '真实桌面实拍 · 可按桌面允许的网格调整大小', [
              ('tablet-widget-small', '今日·小', 64, 245, 420),
              ('tablet-widget-today', '今日', 64, 720, 700),
              ('tablet-widget-two-day', '两日', 64, 1060, 700),
              ('tablet-widget-week', '本周 · 调整高度后', 840, 245, 696)], 1420)


if __name__ == '__main__':
    main()
