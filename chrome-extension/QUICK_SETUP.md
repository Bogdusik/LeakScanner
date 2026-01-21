# Быстрая настройка GitHub токена

## Способ 1: Через консоль расширения (Рекомендуется)

1. Откройте `chrome://extensions/`
2. Найдите "LeakScanner"
3. Нажмите "Service Worker" (или "background page")
4. Откроется консоль разработчика
5. Вставьте и выполните этот код:

```javascript
chrome.storage.sync.set({
  githubToken: 'YOUR_GITHUB_TOKEN_HERE'
}).then(() => {
  console.log('✅ GitHub token added!');
});
```

6. Закройте консоль и откройте popup расширения - токен будет сохранен

## Способ 2: Через настройки расширения

1. Откройте любое расширение LeakScanner (popup)
2. Нажмите иконку ⚙️ (Settings)
3. Вставьте токен в поле "GitHub Token"
4. Нажмите "Save Settings"

## Способ 3: Через консоль на странице GitHub

1. Откройте любую страницу GitHub
2. Нажмите F12 (открыть DevTools)
3. Перейдите на вкладку "Console"
4. Вставьте и выполните:

```javascript
chrome.storage.sync.set({
  githubToken: 'YOUR_GITHUB_TOKEN_HERE'
}).then(() => {
  alert('✅ GitHub token added! Reload the extension.');
});
```
