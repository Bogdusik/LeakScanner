# Структура проекта LeakScanner

## Обзор

LeakScanner - это полнофункциональная система для сканирования безопасности репозиториев GitHub/GitLab, состоящая из Chrome расширения и Spring Boot backend.

## Структура директорий

```
LeakScanner/
│
├── chrome-extension/          # Chrome Extension (Frontend)
│   ├── src/
│   │   ├── popup/             # Popup интерфейс
│   │   │   ├── App.tsx       # Главный компонент
│   │   │   └── components/   # React компоненты
│   │   ├── content/          # Content scripts для GitHub/GitLab
│   │   ├── background/       # Background service worker
│   │   ├── services/         # API клиент
│   │   ├── store/            # Zustand state management
│   │   ├── types/            # TypeScript типы
│   │   ├── utils/            # Утилиты
│   │   ├── styles/           # CSS стили
│   │   ├── icons/            # Иконки расширения
│   │   └── manifest.json     # Manifest v3
│   ├── package.json
│   ├── vite.config.ts        # Vite конфигурация
│   └── tsconfig.json
│
├── backend/                   # Spring Boot Backend
│   ├── src/main/java/com/leakscanner/
│   │   ├── LeakScannerApplication.java
│   │   ├── controller/       # REST контроллеры
│   │   │   └── ScanController.java
│   │   ├── service/          # Бизнес-логика
│   │   │   ├── ScanService.java
│   │   │   ├── SecurityScannerService.java
│   │   │   ├── SecretScannerService.java
│   │   │   ├── VulnerabilityScannerService.java
│   │   │   ├── DependencyScannerService.java
│   │   │   ├── GitHubService.java
│   │   │   ├── GitLabService.java
│   │   │   ├── NpmAuditService.java
│   │   │   ├── SnykService.java
│   │   │   └── NpmRegistryService.java
│   │   ├── model/            # JPA сущности
│   │   │   ├── Repository.java
│   │   │   ├── ScanResult.java
│   │   │   ├── SecretLeak.java
│   │   │   ├── Vulnerability.java
│   │   │   └── OutdatedDependency.java
│   │   ├── dto/              # Data Transfer Objects
│   │   ├── repository/       # JPA репозитории
│   │   ├── mapper/           # Entity-DTO мапперы
│   │   └── config/           # Конфигурация Spring
│   ├── src/main/resources/
│   │   └── application.yml   # Конфигурация приложения
│   ├── pom.xml
│   └── Dockerfile
│
├── database/
│   └── init.sql              # SQL схема PostgreSQL
│
├── aws-lambda/                # AWS Lambda функция (опционально)
│   ├── src/main/java/com/leakscanner/lambda/
│   │   └── ScanHandler.java
│   └── pom.xml
│
├── docker-compose.yml        # Docker Compose конфигурация
├── README.md                  # Основная документация
├── SETUP.md                   # Инструкция по установке
└── .gitignore

```

## Компоненты системы

### 1. Chrome Extension

**Технологии:**
- React 18 + TypeScript
- Vite для сборки
- Tailwind CSS для стилей
- Zustand для state management
- Manifest V3

**Основные файлы:**
- `popup/App.tsx` - главный UI компонент
- `content/index.ts` - скрипт для инжекции в GitHub/GitLab страницы
- `background/index.ts` - service worker
- `services/api.ts` - клиент для REST API

### 2. Spring Boot Backend

**Технологии:**
- Spring Boot 3.2
- Spring Data JPA
- Spring WebFlux
- PostgreSQL
- Caffeine Cache
- Lombok

**Основные сервисы:**
- `ScanService` - оркестрация сканирования
- `SecretScannerService` - поиск секретов по паттернам
- `VulnerabilityScannerService` - анализ уязвимостей
- `DependencyScannerService` - проверка зависимостей
- `GitHubService` / `GitLabService` - интеграция с API

### 3. База данных

**Схема:**
- `repositories` - информация о репозиториях
- `scan_results` - результаты сканирований
- `secret_leaks` - найденные секреты
- `vulnerabilities` - уязвимости
- `outdated_dependencies` - устаревшие зависимости

### 4. Интеграции

- **GitHub API** - получение файлов репозитория
- **GitLab API** - работа с GitLab
- **npm Registry** - проверка версий пакетов
- **Snyk API** - расширенный анализ уязвимостей

## Поток данных

```
1. Пользователь открывает репозиторий на GitHub/GitLab
   ↓
2. Content script определяет репозиторий
   ↓
3. Пользователь нажимает "Scan Repository" в popup
   ↓
4. Extension отправляет POST /api/v1/scan на backend
   ↓
5. Backend:
   - Получает файлы через GitHub/GitLab API
   - Сканирует на секреты (параллельно)
   - Проверяет уязвимости (параллельно)
   - Анализирует зависимости (параллельно)
   ↓
6. Результаты сохраняются в PostgreSQL
   ↓
7. Backend возвращает ScanResultDTO
   ↓
8. Extension отображает результаты в UI
```

## Безопасность

- Токены хранятся локально в Chrome storage
- API ключи передаются через HTTP headers
- Rate limiting для защиты API
- Валидация входных данных
- Защита от SQL injection через JPA

## Масштабирование

- Кэширование результатов сканирования
- Асинхронная обработка через WebFlux
- Параллельное выполнение сканирований
- Опциональная интеграция с AWS Lambda для serverless

## Расширяемость

Система легко расширяется:
- Новые паттерны секретов в `SecretScannerService`
- Новые источники уязвимостей в `VulnerabilityScannerService`
- Поддержка новых package managers в `DependencyScannerService`
- Интеграция с другими security tools
