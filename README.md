# LeakScanner - Security Scanner Extension

A fully functional Chrome extension for scanning GitHub/GitLab repositories for security vulnerabilities, secret leaks, and outdated dependencies.

## 🚀 Features

- **Secret Scanning**: Automatic detection of API keys, tokens, passwords, and other secrets
- **Vulnerability Analysis**: Integration with npm audit and Snyk for dependency vulnerability detection
- **Dependency Checking**: Identification of outdated packages and update recommendations
- **Security Score**: Automatic calculation of repository security score (0-100)
- **Scan History**: Results saved to database for tracking changes over time
- **Caching**: Performance optimization through result caching

## 🛠 Technologies

### Frontend (Chrome Extension)
- **React 18** with TypeScript
- **Vite** for building
- **Tailwind CSS** for styling
- **Zustand** for state management
- **Lucide React** for icons

### Backend
- **Spring Boot 3.2** with Java 17
- **PostgreSQL** for data storage
- **Spring Data JPA** for database operations
- **WebFlux** for asynchronous HTTP requests
- **Caffeine Cache** for caching
- **Lombok** for reducing boilerplate code

### Integrations
- **GitHub API** for repository file retrieval
- **GitLab API** for GitLab repository operations
- **npm Registry API** for package version checking
- **Snyk API** for advanced vulnerability analysis

### Infrastructure
- **Docker** and **Docker Compose** for local development
- **AWS Lambda** for serverless scanning (optional)

## 📦 Installation

### Prerequisites

- **Docker Desktop** (рекомендуется для быстрого старта)
- ИЛИ:
  - Node.js 18+ and npm
  - Java 17+
  - Maven 3.9+
  - PostgreSQL 15+

**📖 Подробная инструкция:** См. [INSTALLATION.md](./INSTALLATION.md) для пошаговой установки.

### 1. Clone Repository

```bash
git clone https://github.com/Bogdusik/LeakScanner.git
cd LeakScanner
```

### 2. Start Backend

#### With Docker Compose (recommended):

```bash
docker-compose up -d
```

Backend will be available at `http://localhost:8080`

#### Without Docker:

1. Create PostgreSQL database:
```bash
createdb leakscanner
```

2. Run SQL script:
```bash
psql -d leakscanner -f database/init.sql
```

3. Run Spring Boot application:
```bash
cd backend
mvn spring-boot:run
```

### 3. Build Chrome Extension

```bash
cd chrome-extension
npm install
npm run build
```

### 4. Install Extension in Chrome

1. Open Chrome and navigate to `chrome://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked"
4. Select the `chrome-extension/dist` folder

## ⚙️ Configuration

### Environment Variables

1. Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```

2. Update `.env` with your actual database credentials:
```bash
POSTGRES_PASSWORD=your_secure_password_here
DB_PASSWORD=your_secure_password_here
```

3. For Docker Compose, the `.env` file will be automatically loaded.

**Подробнее:** См. [ENVIRONMENT_SETUP.md](./ENVIRONMENT_SETUP.md) для полной документации по настройке переменных окружения, токенов и безопасности.

### Backend

Settings are in `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/${DB_NAME:leakscanner}
    username: ${DB_USERNAME:}
    password: ${DB_PASSWORD:}

leakscanner:
  api:
    github:
      base-url: https://api.github.com
    gitlab:
      base-url: https://gitlab.com/api/v4
    snyk:
      base-url: https://api.snyk.io/v1
```

**Important:** Never commit actual passwords to the repository. Use environment variables or a `.env` file (see `.env.example`).

### Chrome Extension

Settings can be changed in the extension popup:
- Backend API URL (default: `http://localhost:8080`)
- GitHub Token (optional, for private repositories)
- GitLab Token (optional)
- Snyk Token (optional, for enhanced scanning)

## 📖 Usage

1. Open any repository on GitHub or GitLab
2. Click the extension icon in Chrome toolbar
3. Click "Scan Repository"
4. Wait for scan completion
5. Review results:
   - Security Score (overall security rating)
   - Secret Leaks (found secrets)
   - Vulnerabilities (security vulnerabilities)
   - Outdated Dependencies (outdated packages)

## 🔍 Secret Scanning Patterns

The extension detects the following types of secrets:

- AWS Access Keys and Secret Keys
- GitHub and GitLab tokens
- Private keys (RSA, EC, OpenSSH)
- API keys
- Plain text passwords
- JWT tokens
- MongoDB and PostgreSQL connection strings
- Slack tokens
- Stripe keys

## 🏗 Architecture

```
LeakScanner/
├── chrome-extension/     # Chrome Extension (React + TypeScript)
│   ├── src/
│   │   ├── popup/       # Popup UI
│   │   ├── content/     # Content scripts
│   │   ├── background/  # Background service worker
│   │   └── services/    # API client
│   └── dist/            # Built extension
│
├── backend/             # Spring Boot Backend
│   ├── src/main/java/
│   │   ├── controller/ # REST controllers
│   │   ├── service/    # Business logic
│   │   ├── model/      # JPA entities
│   │   ├── repository/ # Data repositories
│   │   └── config/     # Configuration
│   └── pom.xml
│
├── database/            # SQL scripts
│   └── init.sql
│
├── aws-lambda/          # AWS Lambda function (optional)
│   └── src/
│
└── docker-compose.yml   # Docker Compose configuration
```

## 🧪 Development

### Running in Development Mode

**Backend:**
```bash
cd backend
mvn spring-boot:run
```

**Chrome Extension:**
```bash
cd chrome-extension
npm run dev
```

### Testing

```bash
# Backend tests
cd backend
mvn test

# Run specific test
mvn test -Dtest=SecretScannerServiceTest

# Extension build
cd chrome-extension
npm install
npm run build
```

**Подробнее:** См. [TESTING.md](./TESTING.md)

## 📊 API Endpoints

### POST `/api/v1/scan`
Scan a repository

**Request:**
```json
{
  "owner": "octocat",
  "name": "Hello-World",
  "platform": "github"
}
```

**Response:**
```json
{
  "repository": {
    "owner": "octocat",
    "name": "Hello-World",
    "platform": "github"
  },
  "secrets": [...],
  "vulnerabilities": [...],
  "outdatedDependencies": [...],
  "securityScore": 85,
  "lastScanned": "2024-01-15T10:30:00Z"
}
```

### GET `/api/v1/scan/history`
Get scan history

**Query Parameters:**
- `owner`: Repository owner
- `name`: Repository name
- `platform`: Platform (github/gitlab)

## 🔒 Security

- All tokens stored locally in Chrome storage (sync)
- API keys passed via HTTP headers
- Rate limiting for abuse protection
- Input validation
- SQL injection protection via JPA

## 🚀 Deployment

### Backend on AWS

1. Build JAR:
```bash
cd backend
mvn clean package
```

2. Deploy to EC2 or use Elastic Beanstalk

### AWS Lambda

See `aws-lambda/README.md` for Lambda function deployment instructions.

## 🔧 Troubleshooting

Если у вас возникли проблемы при установке или использовании:
- 📖 См. [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) для решения частых проблем
- 📖 См. [INSTALLATION.md](./INSTALLATION.md) для подробной инструкции по установке

## 🤝 Contributing

Pull requests are welcome! Please open an issue to discuss major changes.

## 📝 License

MIT License

## 👤 Author

Bohdan.

---

**Note**: To work with private repositories, you need to configure the corresponding tokens in the extension settings.
