"""Locate installed tools without bundling a JDK, Android SDK, or font."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def sdk_tool(directory, name):
    candidates = (name + '.exe', name + '.bat', name) if os.name == 'nt' else (name,)
    for candidate in candidates:
        path = directory / candidate
        if path.is_file():
            return path
    raise SystemExit('Missing tool: ' + str(directory / name))


def java_home():
    configured = os.environ.get('JAVA_HOME')
    if configured:
        result = Path(configured)
    elif sys.platform == 'darwin' and Path('/usr/libexec/java_home').exists():
        result = Path(subprocess.check_output(['/usr/libexec/java_home', '-v', '17'], text=True).strip())
    else:
        compiler = shutil.which('javac')
        if not compiler:
            raise SystemExit('Install JDK 17 and set JAVA_HOME')
        result = Path(compiler).resolve().parent.parent
    sdk_tool(result / 'bin', 'javac')
    sdk_tool(result / 'bin', 'java')
    return result


def sdk_home():
    configured = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if configured:
        result = Path(configured)
        if not result.is_dir():
            raise SystemExit('Android SDK directory does not exist: ' + str(result))
        return result
    candidates = [Path.home() / 'Library/Android/sdk', Path.home() / 'Android/Sdk']
    if os.environ.get('LOCALAPPDATA'):
        candidates.append(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk')
    for path in candidates:
        if path.is_dir():
            return path
    raise SystemExit('Install Android SDK and set ANDROID_HOME')


def preview_font():
    candidates = [os.environ.get('BACKSCREEN_FONT'),
                  '/System/Library/Fonts/STHeiti Medium.ttc',
                  '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc',
                  'C:/Windows/Fonts/msyh.ttc']
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    raise SystemExit('Set BACKSCREEN_FONT to an installed Chinese TTF/OTF/TTC font')
