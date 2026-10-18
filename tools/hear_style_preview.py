#!/usr/bin/env python3
"""Compile/render Hear design candidates from synthetic PCM; does not access devices."""
import json
import argparse
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from toolchain import ROOT, java_home, sdk_tool, preview_font

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--set', choices=('waveform', 'motion'), default='waveform')
motion = parser.parse_args().set == 'motion'
prefix = 'hear-motion' if motion else 'hear-style'
out = ROOT / ('build/' + prefix + '-candidates')
classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
java = java_home()
renderer = 'HearMotion' if motion else 'HearArt'
sources = [ROOT / 'app/src/com/stepxadvance/backscreentoolbox/SoundWave.java',
           ROOT / 'app/src/com/stepxadvance/backscreentoolbox/SoundSpectrum.java',
           ROOT / ('design/hear/' + renderer + '.java'), ROOT / ('design/hear/' + renderer + 'Preview.java')]
subprocess.run([str(sdk_tool(java / 'bin', 'javac')), '-encoding', 'UTF-8', '-d', str(classes),
                *map(str, sources)], check=True)
command = [str(sdk_tool(java / 'bin', 'java')), '-cp', str(classes),
           'com.stepxadvance.backscreentoolbox.' + renderer + 'Preview']
subprocess.run(command, check=True)
frames = json.loads(subprocess.check_output(command + ['export'], text=True))
font = ImageFont.truetype(preview_font(), 20)
small = ImageFont.truetype(preview_font(), 13)
labels = ('D  频谱山形', 'E  声纹光环', 'F  声波涟漪') if motion else ('A  单线示波', 'B  实心波形', 'C  竖向声带')
details = ('真实频段能量 · 低频→高频', '环形频谱 · 不表示声源方向', '音量驱动 · 氛围动效') if motion else ('一条连续线，显示毫秒级起伏', '同一条波形，改用完整块面', '向上流动，显示约半秒强弱')
images = []
individual = [[], [], []]
for index, group in enumerate(frames):
    image = Image.new('RGB', (912, 510), '#0d120e')
    draw = ImageDraw.Draw(image)
    for style, pixels in enumerate(group):
        left = style * 304
        draw.text((left + 16, 15), labels[style], font=font, fill='#e6ebe4')
        draw.text((left + 16, 47), details[style], font=small, fill='#929b90')
        for y in range(23):
            for x in range(17):
                value = 13 + pixels[y * 17 + x] * 242 // 255
                px, py, radius = left + 24 + x * 16, 89 + y * 16, 4.6
                draw.ellipse((px-radius, py-radius, px+radius, py+radius),
                             fill=(value, min(255, value+3), max(0, value-5)))
        individual[style].append(image.crop((left, 0, left+304, 470)))
    caption = '相同合成输入：低频 → 高频 → 短促声 → 安静 · 非实拍' if motion else '相同合成输入：安静 → 轻声 → 较强声 → 短促声 → 安静 · 非实拍'
    draw.text((16, 479), caption, font=small, fill='#929b90')
    images.append(image)
duration = [20 if n % 2 == 0 else 30 for n in range(len(images))]
images[0].save(out / (prefix + '-comparison.gif'), save_all=True, append_images=images[1:], duration=duration, loop=0)
names = ('d-spectrum', 'e-halo', 'f-ripples') if motion else ('a-line', 'b-solid', 'c-ribbon')
for style, name in enumerate(names):
    individual[style][0].save(out / ('hear-' + name + '.gif'), save_all=True,
                              append_images=individual[style][1:], duration=duration, loop=0)
images[35].save(out / (prefix + '-comparison.png'))
sheet = Image.new('RGB', (912, 510 * 3), '#0d120e')
for row, n in enumerate((26, 83, 113) if motion else (26, 83, 117)):
    sheet.paste(images[n], (0, row * 510))
sheet.save(out / (prefix + '-keyframes.png'))
with Image.open(out / (prefix + '-comparison.gif')) as gif:
    total = 0
    for n in range(gif.n_frames):
        gif.seek(n)
        total += gif.info['duration']
    assert total == 4000, total
print(out / (prefix + '-comparison.gif'))
