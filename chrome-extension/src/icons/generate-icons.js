#!/usr/bin/env node

/**
 * Generate PNG icons from SVG using Node.js
 * Requires: sharp package (npm install sharp)
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SVG_FILE = path.join(__dirname, 'icon.svg');
const SIZES = [16, 48, 128];

async function generateIcons() {
  try {
    // Try to use sharp if available
    let sharp;
    try {
      sharp = (await import('sharp')).default;
    } catch (e) {
      console.error('❌ Error: sharp package not found.');
      console.log('\n📦 Install it with: npm install sharp');
      console.log('   Or use one of the other methods in README.md\n');
      process.exit(1);
    }

    if (!fs.existsSync(SVG_FILE)) {
      console.error(`❌ Error: ${SVG_FILE} not found`);
      process.exit(1);
    }

    console.log('🎨 Generating icons from SVG...\n');

    for (const size of SIZES) {
      const outputFile = path.join(__dirname, `icon${size}.png`);
      
      await sharp(SVG_FILE)
        .resize(size, size, {
          fit: 'contain',
          background: { r: 0, g: 0, b: 0, alpha: 0 }
        })
        .png()
        .toFile(outputFile);
      
      console.log(`✅ Generated: icon${size}.png (${size}x${size})`);
    }

    console.log('\n🎉 All icons generated successfully!');
    console.log('📁 Files are ready in:', __dirname);
    
  } catch (error) {
    console.error('❌ Error generating icons:', error.message);
    process.exit(1);
  }
}

generateIcons();
