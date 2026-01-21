#!/bin/bash

# Скрипт для загрузки кода в GitLab
# Использование: ./push-to-gitlab.sh

echo "🚀 Загрузка LeakScanner в GitLab..."
echo ""

# Проверка, что мы в правильной директории
if [ ! -d ".git" ]; then
    echo "❌ Ошибка: .git директория не найдена. Убедитесь, что вы в корне проекта."
    exit 1
fi

# Проверка remote
if ! git remote get-url origin > /dev/null 2>&1; then
    echo "❌ Ошибка: Remote 'origin' не настроен."
    exit 1
fi

echo "✅ Git репозиторий настроен"
echo ""

# Проверка статуса
echo "📊 Статус репозитория:"
git status --short
echo ""

# Если есть незакоммиченные изменения
if ! git diff-index --quiet HEAD --; then
    echo "⚠️  Обнаружены незакоммиченные изменения."
    read -p "Хотите закоммитить их? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git add .
        git commit -m "Update: LeakScanner with ROG Strix UI improvements"
    fi
fi

echo ""
echo "📤 Загрузка в GitLab..."
echo ""
echo "💡 Если GitLab запросит аутентификацию:"
echo "   Username: ваш GitLab username (bogdus1k)"
echo "   Password: ваш Personal Access Token (НЕ пароль от аккаунта!)"
echo ""
echo "   Если у вас нет токена, создайте его здесь:"
echo "   https://gitlab.com/-/user_settings/personal_access_tokens"
echo "   Scopes: write_repository"
echo ""

# Попытка push
if git push -u origin main; then
    echo ""
    echo "✅ Успешно! Код загружен в GitLab"
    echo "🌐 Откройте: https://gitlab.com/bogdusik-group/LeakScanner"
else
    echo ""
    echo "❌ Ошибка при загрузке. Возможные причины:"
    echo "   1. Неверные учетные данные"
    echo "   2. Нет доступа к репозиторию"
    echo "   3. Проблемы с сетью"
    echo ""
    echo "💡 Решение:"
    echo "   1. Создайте Personal Access Token:"
    echo "      https://gitlab.com/-/user_settings/personal_access_tokens"
    echo "   2. Используйте токен как пароль при push"
    echo ""
    echo "   Или используйте SSH:"
    echo "   git remote set-url origin git@gitlab.com:bogdusik-group/LeakScanner.git"
fi
