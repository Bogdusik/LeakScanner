# LeakScanner

A Chrome extension that automatically scans GitHub and GitLab repositories for security vulnerabilities, secret leaks, and outdated dependencies. Built to help developers catch security issues before they become problems, with a beautiful ROG Strix-themed UI and comprehensive security scoring.

## Demo

![Extension Popup](screenshots/extension.png)
![Scan Results](screenshots/scan-results.png)
![Security Dashboard](screenshots/dashboard.png)

## Why It's Cool

- **Automated Security Scanning**: Detects API keys, tokens, passwords, and other secrets automatically using pattern matching and regex
- **Comprehensive Vulnerability Analysis**: Integrates with npm audit and Snyk API for deep dependency vulnerability detection
- **Security Score & History**: Calculates a 0-100 security score and tracks scan history over time with PostgreSQL persistence
- **Performance Optimized**: Uses Caffeine cache for fast repeated scans and WebFlux for reactive, non-blocking API calls
- **Beautiful ROG Strix UI**: Custom-designed Chrome extension interface with Tailwind CSS and smooth animations
- **Multi-Platform Support**: Works with both GitHub and GitLab repositories, with optional token support for private repos

## Tech Stack

- **Backend**: Spring Boot 3.2 (Java 17), Spring WebFlux, PostgreSQL, Spring Data JPA, Caffeine Cache, Lombok
- **Frontend**: React 18 + TypeScript, Tailwind CSS, Vite, Zustand, Lucide React
- **DevOps**: Docker Compose, optional AWS Lambda deployment
- **APIs**: GitHub API, GitLab API, Snyk API, npm Registry API

## How to Run Locally

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Bogdusik/LeakScanner.git
   cd LeakScanner
   ```

2. **Start the backend with Docker Compose:**
   ```bash
   docker-compose up -d
   ```
   Backend will be available at `http://localhost:8080`

- **Docker Desktop** (recommended for quick start)
- OR:
  - Node.js 18+ and npm
  - Java 17+
  - Maven 3.9+
  - PostgreSQL 15+

**Alternative (manual setup):**
   - Create PostgreSQL database: `createdb leakscanner`
   - Run SQL script: `psql -d leakscanner -f database/init.sql`
   - Start Spring Boot: `cd backend && mvn spring-boot:run`

**📖 Detailed instructions:** See [INSTALLATION.md](./INSTALLATION.md) for step-by-step installation.

3. **Build the Chrome extension:**
   ```bash
   cd chrome-extension
   npm install
   npm run build
   ```

4. **Install extension in Chrome:**
   - Open `chrome://extensions/`
   - Enable "Developer mode"
   - Click "Load unpacked"
   - Select the `chrome-extension/dist` folder

5. **Configure environment variables:**
   ```bash
   cp .env.example .env
   # Update .env with your database credentials
   ```

> **Important**: Never hardcode secrets. Always use `.env` file for sensitive data.

## What I Learned

- **Security Scanning in Practice**: Implemented pattern-based secret detection and vulnerability analysis workflows
- **Reactive Programming**: Worked with Spring WebFlux for non-blocking, reactive HTTP requests and API integrations
- **External API Integration**: Integrated multiple third-party APIs (Snyk, GitHub, GitLab) with proper error handling and rate limiting
- **Chrome Extension Development**: Built a full-featured extension with React, TypeScript, and Chrome APIs for content scripts and background workers
- **Caching Strategies**: Implemented Caffeine cache for performance optimization and scan result caching

<<<<<<< HEAD
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

**More details:** See [INSTALLATION.md](./INSTALLATION.md) for full documentation on environment variables, tokens, and security setup.

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

**More details:** See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for troubleshooting.

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

If you encounter any issues during installation or usage:
- 📖 See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for common problem solutions
- 📖 See [INSTALLATION.md](./INSTALLATION.md) for detailed installation instructions

## 🤝 Contributing

Pull requests are welcome! Please open an issue to discuss major changes.

## 📝 License

MIT License

## 👤 Author

Bohdan.

---

**Note**: To work with private repositories, you need to configure the corresponding tokens in the extension settings.

Fork it, use it, improve it — open to PRs!
