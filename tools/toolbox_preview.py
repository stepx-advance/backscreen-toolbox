#!/usr/bin/env python3
"""Render production native-grid scenes; optionally include synthetic Android demo text."""
import argparse
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from toolchain import ROOT, java_home, sdk_tool, preview_font

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--native-demo', type=Path, help='Synthetic demo-frame captures only; never live notification data')
args = parser.parse_args()
out = ROOT / 'build/toolbox-preview'
out.mkdir(parents=True, exist_ok=True)
command = [str(sdk_tool(java_home() / 'bin', 'java')), '-cp', str(ROOT / 'build/tests'),
           'com.stepxadvance.backscreentoolbox.ToolboxTest']
full = json.loads(subprocess.check_output(command + ['export']))
compact = json.loads(subprocess.check_output(command + ['export-compact']))
native = json.loads(args.native_demo.read_text()) if args.native_demo else None

if native:
    for mode in ('message', 'code', 'music', 'frog'):
        assert len(native[mode]) >= 40, 'Incomplete synthetic capture'
        for sample in native[mode]:
            pixels = sample['pixels']
            assert len(pixels) == 391 and all(0 <= v <= 255 for v in pixels)
            assert not any(pixels[:51] + pixels[340:]), 'Expected compact composition'
    for n in range(len(full)):
        for k, mode in enumerate(('message', 'code', 'music', 'frog'), 1):
            sample = native[mode][min(n, len(native[mode]) - 1)]['pixels']
            compact[n][k] = sample
            if mode == 'message':
                full[n][k] = sample
            elif mode == 'music':
                for y in range(9, 20):
                    full[n][k][(y + 3) * 17:(y + 4) * 17] = sample[y * 17:(y + 1) * 17]
            elif mode == 'code':
                # Both layouts share glyphs, with explicit source/code baselines.
                portrait = [0] * 391
                source_page = 160 in sample[4 * 17:7 * 17]
                if source_page:
                    portrait[4 * 17:7 * 17] = sample[4 * 17:7 * 17]
                    for y in range(9, 20):
                        portrait[(y + 1) * 17:(y + 2) * 17] = sample[y * 17:(y + 1) * 17]
                else:
                    for y in list(range(4, 11)) + list(range(12, 19)):
                        portrait[(y + 4) * 17:(y + 5) * 17] = sample[y * 17:(y + 1) * 17]
                full[n][k] = portrait

font = ImageFont.truetype(preview_font(), 18)
small = ImageFont.truetype(preview_font(), 14)
labels = ['计时器', '新消息', '验证码', '音乐伴随', '奶蛙大笑']
images = []
for n in range(len(full)):
    im = Image.new('RGB', (1150, 650), '#101310')
    draw = ImageDraw.Draw(im)
    draw.text((24, 12), '竖屏 · 17 × 23', font=small, fill='#a0aa9d')
    draw.text((24, 351), '横屏 · 中央安全区域', font=small, fill='#a0aa9d')
    for row, frames in enumerate((full[n], compact[n])):
        for k, pixels in enumerate(frames):
            draw.text((k * 230 + 24, 39 + row * 339), labels[k], font=font, fill='#b5f47c')
            width, height = (17, 23) if row == 0 else (23, 17)
            step = 10 if row == 0 else 8
            origin_x = k * 230 + (230 - (width - 1) * step) // 2
            origin_y = 86 if row == 0 else 426
            radius = step * .34
            for y in range(height):
                for x in range(width):
                    v = pixels[y * 17 + x] if row == 0 else pixels[(y + 3) * 17 + x - 3] if 3 <= x < 20 else 0
                    value = 12 + v * 243 // 255
                    px, py = origin_x + x * step, origin_y + y * step
                    draw.ellipse((px - radius, py - radius, px + radius, py + radius),
                                 fill=(value, min(255, value + 3), max(0, value - 5)))
    caption = '绘图代码预览 · 含手机生成的示例文字 · 非实拍' if native else '绘图代码预览 · 歌名与来源由 Android 本机字体绘制，此图未包含 · 非实拍'
    draw.text((24, 616), caption, font=small, fill='#a0aa9d')
    images.append(im)
images[48].save(out / 'toolbox-scenes.png')
images[0].save(out / 'toolbox-scenes.gif', save_all=True, append_images=images[1:], duration=100, loop=0)
print(out / 'toolbox-scenes.gif')
