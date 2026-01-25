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

   **Alternative (manual setup):**
   - Create PostgreSQL database: `createdb leakscanner`
   - Run SQL script: `psql -d leakscanner -f database/init.sql`
   - Start Spring Boot: `cd backend && mvn spring-boot:run`

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

Fork it, use it, improve it — open to PRs!
