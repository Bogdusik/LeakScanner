export interface Repository {
  owner: string;
  name: string;
  platform: 'github' | 'gitlab';
}

export interface SecretLeak {
  type: string;
  file: string;
  line: number;
  severity: 'critical' | 'high' | 'medium' | 'low';
  pattern?: string;
}

export interface Vulnerability {
  title: string;
  description: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  package?: string;
  cve?: string;
  url?: string;
}

export interface OutdatedDependency {
  name: string;
  currentVersion: string;
  latestVersion: string;
  type: 'npm' | 'maven' | 'gradle' | 'pip' | 'other';
}

export interface ScanResult {
  repository: Repository;
  secrets: SecretLeak[];
  vulnerabilities: Vulnerability[];
  outdatedDependencies: OutdatedDependency[];
  securityScore: number;
  lastScanned: string;
  error?: string;
}

export interface ScanProgress {
  type: 'secrets' | 'vulnerabilities' | 'dependencies' | 'progress' | 'complete' | 'error';
  secrets?: SecretLeak[];
  vulnerabilities?: Vulnerability[];
  outdatedDependencies?: OutdatedDependency[];
  progress?: number;
  status?: string;
  message?: string;
  securityScore?: number;
  finalResult?: ScanResult;
}
