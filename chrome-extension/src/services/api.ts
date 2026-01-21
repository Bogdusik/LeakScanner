import axios from 'axios';
import { Repository, ScanResult } from '../types';

const getApiUrl = async (): Promise<string> => {
  const settings = await chrome.storage.sync.get('apiUrl');
  return settings.apiUrl || 'http://localhost:8080';
};

const getAuthHeaders = async () => {
  const settings = await chrome.storage.sync.get(['githubToken', 'gitlabToken', 'snykToken']);
  const headers: Record<string, string> = {};
  
  if (settings.githubToken) {
    headers['X-GitHub-Token'] = settings.githubToken;
  }
  if (settings.gitlabToken) {
    headers['X-GitLab-Token'] = settings.gitlabToken;
  }
  if (settings.snykToken) {
    headers['X-Snyk-Token'] = settings.snykToken;
  }
  
  return headers;
};

export const scanRepository = async (repository: Repository): Promise<ScanResult> => {
  const apiUrl = await getApiUrl();
  const headers = await getAuthHeaders();
  
  const response = await axios.post(
    `${apiUrl}/api/v1/scan`,
    repository,
    { 
      headers,
      timeout: 120000, // 2 минуты таймаут
    }
  );
  
  return response.data;
};

export const getScanHistory = async (repository: Repository): Promise<ScanResult[]> => {
  const apiUrl = await getApiUrl();
  const headers = await getAuthHeaders();
  
  const response = await axios.get(
    `${apiUrl}/api/v1/scan/history`,
    {
      params: repository,
      headers,
      timeout: 30000, // 30 секунд таймаут
    }
  );
  
  return response.data;
};
