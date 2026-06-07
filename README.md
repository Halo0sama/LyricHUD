# LyricHUD

Minecraft Forge 模组，在游戏内实时显示当前播放音乐的歌词和曲目信息。

## 功能

- 自动识别资源包中的音乐（文件名解析 / AcoustID 音频指纹 / ACRCloud）
- 纯音乐显示曲名和艺术家
- 带歌词的歌显示网易云双语 LRC（原文 + 翻译）
- HUD 弹入弹出动画（滑入 1s → 显示 5s → 滑出 1s）
- 主菜单和游戏内均可使用
- 歌词错误可一键报告，下次自动跳过错误匹配
- Mod 配置界面开关

## 安装

1. 下载 `lyrichud-1.2.0.jar`
2. 放入 `.minecraft/mods/` 文件夹
3. 需要 Minecraft Forge 1.20.1 (47.x)

## 配置

游戏中 **Mods → LyricHUD → Config** 可设置：

- `show_hud`：HUD 总开关
- `acr_host / acr_key / acr_secret`：ACRCloud API（可选，注册免费额度 100次/天，大幅提升冷门曲目识别率）
- `report_wrong`：拨到 ON 报告当前歌词错误

## 识别链路

```
音乐播放
  ├─ ① 文件名解析 → 网易云/酷我搜歌词
  ├─ ② AcoustID 音频指纹（免费，不限量）
  ├─ ③ ACRCloud（需配置 API key）
  └─ ④ 未匹配到 → 不显示，不猜测
```

## 命令

- `/lyricreport`：报告当前歌词错误，下次自动跳过

## 许可证

GNU General Public License v3.0

本项目部分代码移植自 music-tag-web (GPL V3.0)：
https://github.com/xhongc/music-tag-web

音频指纹使用 AcoustID / Chromaprint。
