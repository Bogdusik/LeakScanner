import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { copyFileSync, mkdirSync, cpSync, existsSync, readFileSync, writeFileSync, readdirSync } from 'fs';

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'copy-manifest',
      writeBundle() {
        const distDir = resolve(__dirname, 'dist');
        mkdirSync(distDir, { recursive: true });
        
        // Copy manifest.json
        copyFileSync(
          resolve(__dirname, 'src/manifest.json'),
          resolve(__dirname, 'dist/manifest.json')
        );
        
        // Copy and update popup.html (after popup.js is built)
        const popupHtmlSrc = resolve(__dirname, 'src/popup.html');
        const popupHtmlDist = resolve(__dirname, 'dist/popup.html');
        if (existsSync(popupHtmlSrc)) {
          let popupHtml = readFileSync(popupHtmlSrc, 'utf-8');
          
          // Find CSS file in assets directory
          const assetsDir = resolve(__dirname, 'dist/assets');
          if (existsSync(assetsDir)) {
            const files = readdirSync(assetsDir);
            const cssFile = files.find((f: string) => f.endsWith('.css') && f.startsWith('popup-'));
            if (cssFile) {
              // Add CSS link before closing </head>
              if (!popupHtml.includes('assets/')) {
                popupHtml = popupHtml.replace(
                  '</head>',
                  `  <link rel="stylesheet" href="./assets/${cssFile}">\n</head>`
                );
              }
            }
          }
          
          writeFileSync(popupHtmlDist, popupHtml);
        }
        
        // Copy icons if they exist
        const iconsSrc = resolve(__dirname, 'src/icons');
        const iconsDist = resolve(__dirname, 'dist/icons');
        if (existsSync(iconsSrc)) {
          mkdirSync(iconsDist, { recursive: true });
          cpSync(iconsSrc, iconsDist, { recursive: true });
        }
        
        // Copy content.css
        const contentCssSrc = resolve(__dirname, 'src/content/content.css');
        const contentCssDist = resolve(__dirname, 'dist/content.css');
        if (existsSync(contentCssSrc)) {
          copyFileSync(contentCssSrc, contentCssDist);
        }
      },
    },
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      input: {
        popup: resolve(__dirname, 'src/popup.tsx'),
        background: resolve(__dirname, 'src/background/index.ts'),
        content: resolve(__dirname, 'src/content/index.ts'),
      },
      output: {
        entryFileNames: '[name].js',
      },
    },
  },
});
