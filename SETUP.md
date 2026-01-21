# LeakScanner - Инструкция по установке и запуску

## Быстрый старт

### 1. Запуск с Docker Compose (самый простой способ)

```bash
# Клонируйте репозиторий (если еще не сделали)
cd LeakScanner

# Запустите все сервисы
docker-compose up -d

# Проверьте статус
docker-compose ps

# Просмотрите логи backend
docker-compose logs -f backend
```

Backend будет доступен на `http://localhost:8080`

### 2. Сборка Chrome Extension

```bash
cd chrome-extension

# Установите зависимости
npm install

# Соберите расширение
npm run build
```

### 3. Установка Extension в Chrome

1. Откройте Chrome
2. Перейдите в `chrome://extensions/`
3. Включите "Режим разработчика" (Developer mode)
4. Нажмите "Загрузить распакованное расширение" (Load unpacked)
5. Выберите папку `chrome-extension/dist`

### 4. Настройка Extension

1. Откройте любое расширение на GitHub или GitLab
2. Нажмите на иконку LeakScanner в панели инструментов
3. Нажмите на иконку настроек (⚙️)
4. Убедитесь, что Backend API URL установлен на `http://localhost:8080`
5. (Опционально) Добавьте токены для GitHub/GitLab/Snyk

## Ручная установка (без Docker)

### Требования

- Java 17+
- Maven 3.9+
- PostgreSQL 15+
- Node.js 18+
- npm

### Шаги

#### 1. Настройка PostgreSQL

```bash
# Создайте базу данных
createdb leakscanner

# Или через psql
psql -U postgres
CREATE DATABASE leakscanner;
\q

# Запустите SQL скрипт
psql -d leakscanner -f database/init.sql
```

#### 2. Запуск Backend

```bash
cd backend

# Настройте application.yml если нужно
# Измените настройки БД в src/main/resources/application.yml

# Запустите приложение
mvn spring-boot:run
```

#### 3. Сборка и установка Extension

См. шаги выше в разделе "Быстрый старт"

## Проверка работы

### Тест Backend API

```bash
# Проверьте health endpoint
curl http://localhost:8080/actuator/health

# Протестируйте сканирование
curl -X POST http://localhost:8080/api/v1/scan \
  -H "Content-Type: application/json" \
  -d '{
    "owner": "octocat",
    "name": "Hello-World",
    "platform": "github"
  }'
```

### Тест Extension

1. Откройте https://github.com/octocat/Hello-World
2. Нажмите на иконку расширения
3. Нажмите "Scan Repository"
4. Дождитесь результатов

## Устранение проблем

### Backend не запускается

- Проверьте, что PostgreSQL запущен и доступен
- Убедитесь, что порт 8080 свободен
- Проверьте логи: `docker-compose logs backend`

### Extension не работает

- Проверьте консоль браузера (F12)
- Убедитесь, что backend доступен по указанному URL
- Проверьте настройки расширения

### Ошибки подключения к API

- Проверьте CORS настройки в backend
- Убедитесь, что backend запущен
- Проверьте URL в настройках расширения

## Разработка

### Hot Reload для Backend

```bash
cd backend
mvn spring-boot:run
# Изменения в коде будут автоматически перезагружены
```

### Hot Reload для Extension

```bash
cd chrome-extension
npm run dev
# После изменений перезагрузите расширение в Chrome
```

## Production Deployment

### Backend на сервере

1. Соберите JAR:
```bash
cd backend
mvn clean package
```

2. Запустите:
```bash
java -jar target/leakscanner-backend-1.0.0.jar
```

### Extension в Chrome Web Store

1. Соберите production версию:
```bash
cd chrome-extension
npm run build
```

2. Создайте ZIP из папки `dist`
3. Загрузите в Chrome Web Store Developer Dashboard

## Дополнительная информация

См. основной [README.md](README.md) для подробной документации.
