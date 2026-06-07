# LyricHUD

Minecraft Forge mod that displays synced lyrics with translations on the HUD when music plays.

## Features

- 🎵 Auto-detects music playing (vanilla, resource packs)
- 🔍 Multi-source song identification (AcoustID, ACRCloud)
- 📝 Fetches lyrics from NetEase Cloud Music with built-in translation
- 🎹 Instrumental music: shows track name + composer
- 🎬 Smooth slide-in/out animation
- ⚙️ In-game config (Mods → LyricHUD → Config)

## How It Works

```
Music plays → Identify song (AcoustID/ACRCloud)
           → Search NetEase for lyrics + translation
           → Display on HUD (original + translated)
```

## Installation

1. Download the `.jar` from [Releases](https://github.com/Halo0sama/LyricHUD/releases)
2. Put it in your `mods` folder
3. Launch Minecraft with Forge 1.20.1

## Optional: ACRCloud

For better song identification coverage, register at [ACRCloud](https://console.acrcloud.cn/) and fill in your API key in Mods → LyricHUD → Config.

## Credits

- Lyrics fetching adapted from [music-tag-web](https://github.com/xhongc/music-tag-web) (GPL V3.0)
- Audio fingerprinting via [Chromaprint](https://github.com/acoustid/chromaprint)

## License

GPL V3.0 — see [LICENSE](LICENSE)
