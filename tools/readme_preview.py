#!/usr/bin/env python3
"""Export a README SVG from production pixel frames and synthetic inputs only."""
import json
import subprocess
from html import escape

from toolchain import ROOT, java_home, sdk_tool


def export(name):
    command = [str(sdk_tool(java_home() / 'bin', 'java')), '-cp',
               str(ROOT / 'build/tests'),
               'com.stepxadvance.backscreentoolbox.' + name, 'export']
    return json.loads(subprocess.check_output(command, text=True))


def main():
    if not (ROOT / 'build/tests').is_dir():
        raise SystemExit('Run python3 tools/test.py before exporting the README preview')
    charge, tools, hear = export('FrameTest'), export('ToolboxTest'), export('SoundSpectrumTest')
    scenes = [
        ('充电', '真实电量与充电状态', charge['charging'][16]),
        ('计时', '专注、做饭与小憩', tools[48][0]),
        ('消息', '新消息与验证码提示', tools[12][1]),
        ('音乐', '播放动效与歌名', tools[12][3]),
        ('奶蛙', '让背屏笑起来', tools[4][4]),
        ('听见', '看见声音里的频率', hear[40]),
    ]
    pieces = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="448" viewBox="0 0 1200 448" role="img" aria-labelledby="title description">',
        '<title id="title">STEPX 背屏工具箱：六种点阵玩法</title>',
        '<desc id="description">充电、计时、消息、音乐、奶蛙和听见。由生产绘图代码导出的合成示意，非实拍；歌名由 Android 本机字体绘制，本图未包含。</desc>',
        '<defs><pattern id="grid" width="7" height="7" patternUnits="userSpaceOnUse"><circle cx="3.5" cy="3.5" r="2.15" fill="#20281f"/></pattern></defs>',
        '<rect width="1200" height="448" rx="24" fill="#101610"/>',
        '<g font-family="PingFang SC,Microsoft YaHei,Noto Sans CJK SC,sans-serif">',
        '<text x="32" y="44" fill="#b5f47c" font-size="13" letter-spacing="2">BACKSCREEN TOOLBOX</text>',
        '<text x="32" y="88" fill="#edf2e9" font-size="31" font-weight="600">把背屏，用起来。</text>',
        '<text x="1168" y="85" text-anchor="end" fill="#a3b09c" font-size="14">17 × 23 点阵 · 六种日常玩法</text>',
    ]
    for n, (title, subtitle, pixels) in enumerate(scenes):
        assert len(pixels) == 391 and all(0 <= v <= 255 for v in pixels)
        x = 24 + n * 194
        pieces += [
            f'<rect x="{x}" y="116" width="182" height="280" rx="17" fill="#192218" stroke="#2a3728"/>',
            f'<text x="{x+18}" y="148" fill="#e6eee0" font-size="18" font-weight="600">{escape(title)}</text>',
            f'<g transform="translate({x+31.5} 166)">',
            '<rect width="119" height="161" rx="3" fill="url(#grid)"/>',
        ]
        for y in range(23):
            for xx in range(17):
                value = pixels[y * 17 + xx]
                if value:
                    brightness = 40 + value * 207 // 255
                    color = f'#{brightness:02x}{min(255,brightness+5):02x}{max(0,brightness-4):02x}'
                    pieces.append(f'<circle cx="{xx*7+3.5}" cy="{y*7+3.5}" r="2.15" fill="{color}"/>')
        pieces += [
            '</g>',
            f'<text x="{x+18}" y="374" fill="#a7b4a0" font-size="12">{escape(subtitle)}</text>',
        ]
    pieces += [
        '<text x="32" y="427" fill="#9ba995" font-size="12">生产绘图代码 · 合成示意，非实拍 · 歌名由手机绘制，此图未包含</text>',
        '</g></svg>',
    ]
    output = ROOT / 'docs/images/readme-overview.svg'
    output.write_text('\n'.join(pieces) + '\n', encoding='utf-8')
    print(output.relative_to(ROOT))


if __name__ == '__main__':
    main()
