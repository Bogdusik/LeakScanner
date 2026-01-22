-- LeakScanner Database Schema

-- Database is already created by POSTGRES_DB environment variable
-- This script runs in the context of the leakscanner database

-- Repositories table
CREATE TABLE IF NOT EXISTS repositories (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(50) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    full_name VARCHAR(500) NOT NULL UNIQUE,
    is_private BOOLEAN DEFAULT FALSE,
    default_branch VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_platform_owner_name ON repositories(platform, owner, name);

-- Scan results table
CREATE TABLE IF NOT EXISTS scan_results (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    security_score INTEGER NOT NULL CHECK (security_score >= 0 AND security_score <= 100),
    secrets_count INTEGER NOT NULL DEFAULT 0,
    vulnerabilities_count INTEGER NOT NULL DEFAULT 0,
    outdated_dependencies_count INTEGER NOT NULL DEFAULT 0,
    scan_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scan_duration_ms BIGINT,
    scan_status VARCHAR(20) NOT NULL CHECK (scan_status IN ('SUCCESS', 'FAILED', 'IN_PROGRESS')),
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_repository_scan_date ON scan_results(repository_id, scan_date DESC);

-- Secret leaks table
CREATE TABLE IF NOT EXISTS secret_leaks (
    id BIGSERIAL PRIMARY KEY,
    scan_result_id BIGINT NOT NULL REFERENCES scan_results(id) ON DELETE CASCADE,
    type VARCHAR(100) NOT NULL,
    file VARCHAR(500) NOT NULL,
    line INTEGER NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    pattern VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_secret_scan_result ON secret_leaks(scan_result_id);

-- Vulnerabilities table
CREATE TABLE IF NOT EXISTS vulnerabilities (
    id BIGSERIAL PRIMARY KEY,
    scan_result_id BIGINT NOT NULL REFERENCES scan_results(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    package_name VARCHAR(200),
    cve VARCHAR(50),
    url VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_vulnerability_scan_result ON vulnerabilities(scan_result_id);

-- Outdated dependencies table
CREATE TABLE IF NOT EXISTS outdated_dependencies (
    id BIGSERIAL PRIMARY KEY,
    scan_result_id BIGINT NOT NULL REFERENCES scan_results(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    current_version VARCHAR(50) NOT NULL,
    latest_version VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('NPM', 'MAVEN', 'GRADLE', 'PIP', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_dependency_scan_result ON outdated_dependencies(scan_result_id);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_repositories_updated_at BEFORE UPDATE ON repositories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
