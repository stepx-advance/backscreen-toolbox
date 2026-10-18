"""Preview production transition frames, including the unchanged compositor blank."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from toolchain import preview_font

ROOT=Path(__file__).resolve().parents[1]
frames=json.loads((ROOT/'build/transition-frames.json').read_text())
out=ROOT/'dist';out.mkdir(exist_ok=True)
font_path=preview_font()
title=ImageFont.truetype(font_path,30)
font=ImageFont.truetype(font_path,22)
small=ImageFont.truetype(font_path,17)

def grid(d,frame,x,y,land,cell):
    w,h=(23,17) if land else (17,23)
    for row in range(h):
        for col in range(w):
            v=frame[(row+3)*17+col-3] if land and 3<=col<20 else 0 if land else frame[row*17+col]
            b=30+v*225//255
            px=x+(col+.5)*cell;py=y+(row+.5)*cell;r=cell*.31
            d.ellipse((px-r,py-r,px+r,py+r),fill=(b,min(255,b+3),max(0,b-5)))

def render(k):
    im=Image.new('RGB',(1100,650),'#101310');d=ImageDraw.Draw(im)
    d.text((35,25),'整组旋转 · 电池与数字一起收拢、轻转、展开',font=title,fill='#edf2e7')
    d.text((35,75),'每次转动只触发一次；此处循环演示。系统黑场约 100ms，动效 180ms。',font=small,fill='#a0aa9d')
    d.rounded_rectangle((25,125,535,615),radius=25,fill='#1a201b')
    d.rounded_rectangle((565,125,1075,615),radius=25,fill='#1a201b')
    d.text((50,148),'竖屏 → 横屏',font=font,fill='#b5f47c')
    d.text((590,148),'横屏 → 竖屏',font=font,fill='#b5f47c')
    grid(d,frames['portrait_to_landscape'][k],73,238,True,18)
    grid(d,frames['landscape_to_portrait'][k],692,213,False,15)
    return im

images=[render(k).quantize(colors=32) for k in range(50)]
images[0].save(out/'rotation-transition-preview.gif',save_all=True,append_images=images[1:],duration=20,loop=0,disposal=2)
render(9).save(out/'rotation-transition-preview.png')

story=Image.new('RGB',(1250,340),'#101310');d=ImageDraw.Draw(story)
for i,k in enumerate([5,7,9,11,14]):
    d.text((i*250+20,20),str(k*20)+'ms',font=font,fill='#b5f47c')
    grid(d,frames['portrait_to_landscape'][k],i*250+10,75,True,10)
story.save(out/'rotation-transition-frames.png')
print(out/'rotation-transition-preview.gif')
