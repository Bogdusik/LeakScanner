// Скрипт для быстрой настройки GitLab токена в расширении
// Запустите этот код в консоли расширения (chrome://extensions -> LeakScanner -> Service worker -> Console)

chrome.storage.sync.set({
  gitlabToken: 'YOUR_GITLAB_TOKEN_HERE'
}, function() {
  console.log('✅ GitLab токен успешно сохранен!');
  console.log('Теперь расширение может сканировать приватные GitLab репозитории');
});
