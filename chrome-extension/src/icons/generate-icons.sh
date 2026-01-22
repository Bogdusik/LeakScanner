#!/bin/bash

# Script to generate PNG icons from SVG
# Requires: ImageMagick or Inkscape

SVG_FILE="icon.svg"
OUTPUT_DIR="."

echo "Generating icons from SVG..."

# Check if ImageMagick is available
if command -v convert &> /dev/null; then
    echo "Using ImageMagick..."
    convert -background none -resize 16x16 "${SVG_FILE}" "${OUTPUT_DIR}/icon16.png"
    convert -background none -resize 48x48 "${SVG_FILE}" "${OUTPUT_DIR}/icon48.png"
    convert -background none -resize 128x128 "${SVG_FILE}" "${OUTPUT_DIR}/icon128.png"
    echo "✅ Icons generated successfully!"
# Check if Inkscape is available
elif command -v inkscape &> /dev/null; then
    echo "Using Inkscape..."
    inkscape --export-type=png --export-filename="${OUTPUT_DIR}/icon16.png" -w 16 -h 16 "${SVG_FILE}"
    inkscape --export-type=png --export-filename="${OUTPUT_DIR}/icon48.png" -w 48 -h 48 "${SVG_FILE}"
    inkscape --export-type=png --export-filename="${OUTPUT_DIR}/icon128.png" -w 128 -h 128 "${SVG_FILE}"
    echo "✅ Icons generated successfully!"
else
    echo "❌ Error: Neither ImageMagick nor Inkscape found."
    echo "Please install one of them:"
    echo "  - ImageMagick: brew install imagemagick (macOS) or apt-get install imagemagick (Linux)"
    echo "  - Inkscape: brew install inkscape (macOS) or apt-get install inkscape (Linux)"
    echo ""
    echo "Or use an online tool: https://cloudconvert.com/svg-to-png"
    exit 1
fi
