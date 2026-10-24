# 从源码构建

## 环境

- Python 3.9+（普通构建只使用标准库）
- JDK 17
- Android SDK `platforms;android-36` 和 `build-tools;36.0.0`

设置 `JAVA_HOME` 与 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）。未设置时，工具会查找 macOS/Linux/Windows 常见的本地路径；SDK/JDK 不随项目分发。Windows 使用 `py -3` 替代 `python3`。

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
python3 tools/test.py
python3 tools/build.py
```

无需 Gradle 或 Maven。`aapt2` 编译资源，`javac` 编译 Java，`d8` 转换 DEX，`zipalign` 对齐后由 `apksigner` 签名。

## 开发构建

默认输出 `dist/backscreen-toolbox-1.1.0-dev.apk` 和同名 `.sha256`。开发密钥及密码在 `.local/` 首次生成并保持本地复用，目录已被 Git 忽略。它与正式签名不同，无法覆盖正式安装；不要把开发构建冒充正式下载发布。

开发构建使用相同包名，安装到已有官方版本的手机前，需自行处理签名冲突及卸载造成的数据清除。无需为测试另建功能不同的应用副本。

## 正式签名

正式构建必须提供已有私钥。先在本地环境设置 `BACKSCREEN_STORE_PASS`，不要将密码写入命令、文档、日志或仓库，然后执行：

```sh
python3 tools/build.py --release --keystore /private/path/release.p12 --ks-key-alias YOUR_ALIAS
```

也可用 `--ks-pass-env` 指定另一个密码环境变量名称。工具不会在缺失正式私钥时自动新建替代密钥。输出 `dist/backscreen-toolbox-1.1.0.apk` 和校验文件。

1.0.0 与 1.1.0 均保留原签名证书，SHA-256：

```text
595b31c9e06240f86a3a4e227d7956d78e9617c01cdae50cd1ee1c6209e86025
```

证书摘要是公开身份信息，私钥和密码不公开。包名为 `com.stepxadvance.backscreentoolbox`，versionName 为 `1.1.0`，versionCode 为 `2`。可直接覆盖公开版 1.0.0；这是独立于旧包名的公开版本线。

## 生成预览

安装 Pillow，并使用本机支持中文的字体；字体不打包分发。

```sh
python3 -m pip install Pillow
python3 tools/test.py
python3 tools/preview.py
python3 tools/transition_preview.py
python3 tools/readme_preview.py
```

默认查找 macOS 黑体、Linux Noto Sans CJK 和 Windows 微软雅黑，也可用 `BACKSCREEN_FONT` 指定 TTF/OTF/TTC 文件。图像输出到 `dist/`，与正式 Java 帧逻辑共用数据；它们是模拟示意，不是真机照片。

`readme_preview.py` 只使用 Python 标准库和上述检查编译出的 Java 导出器，生成 `docs/images/readme-overview.svg`。总览使用合成数据，不读取手机或麦克风；SVG 保留可编辑矢量和文字，歌名由 Android 本机字体绘制，不包含在该总览中。修改绘图代码后先重新运行 `tools/test.py`，再导出总览。

## 自动检查

`.github/workflows/ci.yml` 在 Ubuntu 上运行逻辑/点阵测试并构建开发签名 APK。CI 不持有正式私钥，不发布正式安装包。它验证源码构建，不能代替 PZ261A 真机测试。

Linux 自动检查结果以 Actions 的实际运行记录为准；Windows 脚本路径已适配查找规则，尚未做 Windows 实机构建验收。
