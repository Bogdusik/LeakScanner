# Исправление проблемы с popup.html

## Проблема
Vite встраивает весь JavaScript код в popup.html, из-за чего в Chrome показывается исходный код вместо интерфейса.

## Решение

1. Создан отдельный entry point: `src/popup.tsx`
2. popup.html теперь ссылается на `./popup.js` вместо `./popup/index.tsx`
3. Конфигурация Vite обновлена для правильной обработки

## Сборка

```bash
cd chrome-extension
npm run build
```

После сборки проверьте:
- `dist/popup.html` должен содержать `<script type="module" src="./popup.js"></script>`
- `dist/popup.js` должен существовать
- `dist/content.css` должен существовать
- `dist/manifest.json` должен существовать

## Если проблема осталась

Удалите расширение из Chrome и загрузите заново:
1. chrome://extensions/
2. Удалите старое расширение
3. Load unpacked → выберите `dist/` папку
