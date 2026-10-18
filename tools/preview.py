"""Render the exact Java frame export as a dot-matrix design preview."""
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from toolchain import preview_font

ROOT = Path(__file__).resolve().parents[1]
if not (ROOT/"build/frames.json").exists():subprocess.run([sys.executable,str(ROOT/"tools/test.py")],check=True)
frames = json.loads((ROOT / "build/frames.json").read_text())
out = ROOT / "dist"
out.mkdir(exist_ok=True)
font_path = preview_font()
font = ImageFont.truetype(font_path, 24)
small = ImageFont.truetype(font_path, 17)
title = ImageFont.truetype(font_path, 32)
version=ET.parse(ROOT/'app/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')

def render(k):
    image = Image.new("RGB", (1280, 1010), "#101310")
    d = ImageDraw.Draw(image)
    d.text((45,28), "STEPX 背屏工具箱 "+version, font=title, fill="#e9ede5")
    d.text((45,78), "17 × 23 原生点阵 / 原创图案与时序 / 效果示意，实物亮度跟随手机设置", font=small, fill="#9ca697")
    labels = [("charging", "充电中 · 73%", "能量上行，数字保持稳定"), ("full", "充电完成 · 100%", "对勾与星点收尾，随后静止"), ("bypass", "旁路供电 · 80%", "能量绕行，电池电量保持"), ("waiting", "等待旁路 · 60%", "充至设定电量后进入旁路")]
    for col,(mode,name,caption) in enumerate(labels):
        x = 25 + col * 315
        d.rounded_rectangle((x,130,x+300,648),radius=24,fill="#1a201b")
        d.text((x+22,152),name,font=font,fill="#b5f47c")
        # Exact 391 values from production Java, with round dots approximating the panel.
        f=frames[mode][k]
        for y in range(23):
            for xx in range(17):
                v=f[y*17+xx]
                b=30+v*225//255
                px=x+31+xx*14; py=205+y*16
                d.ellipse((px,py,px+8,py+8),fill=(b,min(255,b+3),max(0,b-5)))
        d.text((x+18,607),caption,font=small,fill="#a0aa9d")
        d.rounded_rectangle((x,675,x+300,955),radius=24,fill="#1a201b")
        d.text((x+20,695),"横屏 · 宽电池 / 一行电量",font=small,fill="#b5f47c")
        f=frames[mode+"_land"][k]
        for y in range(17):
            for xx in range(23):
                v=f[(y+3)*17+xx-3] if 3<=xx<20 else 0
                b=30+v*225//255
                px=x+14+xx*12;py=745+y*11
                d.ellipse((px,py,px+6,py+6),fill=(b,min(255,b+3),max(0,b-5)))
    d.text((30,975),"横屏按当前固件的中央 17×17 安全区域重排；系统完成左右方向旋转。",font=small,fill="#9ca697")
    return image

render(16).save(out / "design-preview.png")
def landscape_preview(k):
    image=Image.new("RGB",(1280,385),"#101310")
    d=ImageDraw.Draw(image)
    d.text((30,20),"横屏新布局 · 充电 / 充满 / 旁路 / 等待旁路",font=title,fill="#e9ede5")
    image.paste(render(k).crop((0,675,1280,955)),(0,85))
    return image
landscape_preview(16).save(out/"landscape-preview.png")
images=[render(k).quantize(colors=32) for k in range(120)]
images[0].save(out / "design-preview.gif",save_all=True,append_images=images[1:],duration=100,loop=0,disposal=2)
print(out / "design-preview.gif")
