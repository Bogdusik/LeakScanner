# LeakScanner Icons

## 📁 Files
- `icon.svg` - Source SVG file with ROG Strix styling
- `icon16.png` - 16x16 pixels (browser toolbar)
- `icon48.png` - 48x48 pixels (extension management)
- `icon128.png` - 128x128 pixels (Chrome Web Store)

## 🎨 Icon Design
The icon features:
- **Shield shape** - Represents security protection
- **RGB gradient** - ROG Strix style (cyan → purple → pink)
- **Scanning effect** - Animated scan lines
- **Checkmark** - Security verification
- **Glowing dots** - Scanning indicators

## 🔧 Generating PNG from SVG

### Option 1: Online Converter (Easiest)
1. Open [CloudConvert SVG to PNG](https://cloudconvert.com/svg-to-png)
2. Upload `icon.svg`
3. Set dimensions: 16x16, 48x48, 128x128
4. Download and rename to `icon16.png`, `icon48.png`, `icon128.png`

### Option 2: ImageMagick
```bash
brew install imagemagick  # macOS
# or
sudo apt-get install imagemagick  # Linux

cd chrome-extension/src/icons
./generate-icons.sh
```

### Option 3: Inkscape
```bash
brew install inkscape  # macOS
# or
sudo apt-get install inkscape  # Linux

inkscape --export-type=png --export-filename=icon16.png -w 16 -h 16 icon.svg
inkscape --export-type=png --export-filename=icon48.png -w 48 -h 48 icon.svg
inkscape --export-type=png --export-filename=icon128.png -w 128 -h 128 icon.svg
```

### Option 4: Node.js (svg2png-cli)
```bash
npm install -g svg2png-cli
svg2png icon.svg --output icon16.png --width 16 --height 16
svg2png icon.svg --output icon48.png --width 48 --height 48
svg2png icon.svg --output icon128.png --width 128 --height 128
```

## 👀 Preview
Open `preview.html` in your browser to see the icon in different sizes.

## ✅ After Generation
Make sure all three PNG files are in this directory:
- `icon16.png`
- `icon48.png`
- `icon128.png`

The build process will automatically copy them to `dist/icons/`.
