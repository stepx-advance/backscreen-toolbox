#!/usr/bin/env python3
"""Render synthetic Hear frames from production Java; never opens a microphone."""
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from toolchain import ROOT, java_home, sdk_tool, preview_font

out = ROOT / 'build/hear-preview'
out.mkdir(parents=True, exist_ok=True)
frames = json.loads(subprocess.check_output([
    str(sdk_tool(java_home() / 'bin', 'java')), '-cp', str(ROOT / 'build/tests'),
    'com.stepxadvance.backscreentoolbox.SoundSpectrumTest', 'export'], text=True))
font = ImageFont.truetype(preview_font(), 15)

def panel(pixels, step=16):
    image = Image.new('RGB', (17 * step, 23 * step), '#0d120e')
    draw = ImageDraw.Draw(image)
    for y in range(23):
        for x in range(17):
            value = pixels[y * 17 + x]
            value = 16 + value * 239 // 255
            px, py, r = x * step + step // 2, y * step + step // 2, step * .29
            draw.ellipse((px-r, py-r, px+r, py+r), fill=(value, min(255, value+3), max(0, value-5)))
    return image

images = []
for frame in frames:
    image = Image.new('RGB', (304, 430), '#0d120e')
    image.paste(panel(frame), (16, 16))
    ImageDraw.Draw(image).text((16, 398), '频谱山形 · 合成示例 · 非实拍', font=font, fill='#a0aa9d')
    images.append(image)
# GIF time units are 10 ms; alternate 20/30 ms to preserve the 25 ms average.
images[0].save(out / 'hear-synthetic-preview.gif', save_all=True, append_images=images[1:], duration=[20 if n % 2 == 0 else 30 for n in range(len(images))], loop=0)
images[40].save(out / 'hear-synthetic-frame.png')
sheet = Image.new('RGB', (304 * 4, 430 * 2), '#0d120e')
for n, index in enumerate((0, 20, 30, 40, 55, 90, 130, 138)):
    sheet.paste(images[index], ((n % 4) * 304, (n // 4) * 430))
sheet.save(out / 'hear-keyframes.png')
scenarios = json.loads(subprocess.check_output([
    str(sdk_tool(java_home() / 'bin', 'java')), '-cp', str(ROOT / 'build/tests'),
    'com.stepxadvance.backscreentoolbox.SoundSpectrumTest', 'scenarios'], text=True))
gallery = Image.new('RGB', (304 * len(scenarios), 430), '#0d120e')
for n, (frame, label) in enumerate(zip(scenarios, ('静音', '低频合成', '高频合成', '宽频噪声', '强输入合成'))):
    gallery.paste(panel(frame), (n * 304 + 16, 16))
    ImageDraw.Draw(gallery).text((n * 304 + 16, 398), label + ' · 非实拍', font=font, fill='#a0aa9d')
gallery.save(out / 'hear-signal-comparison.png')
print(out)
