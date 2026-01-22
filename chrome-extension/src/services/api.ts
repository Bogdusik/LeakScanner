import axios from 'axios';
import { Repository, ScanResult, ScanProgress } from '../types';

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

export const scanRepository = async (repository: Repository, forceRescan: boolean = false): Promise<ScanResult> => {
  const apiUrl = await getApiUrl();
  const headers = await getAuthHeaders();
  
  const response = await axios.post(
    `${apiUrl}/api/v1/scan${forceRescan ? '?force=true' : ''}`,
    repository,
    { 
      headers,
      timeout: 120000, // 2 минуты таймаут
    }
  );
  
  return response.data;
};

export const scanRepositoryStream = async (
  repository: Repository,
  onProgress: (progress: ScanProgress) => void,
  forceRescan: boolean = false
): Promise<ScanResult> => {
  const apiUrl = await getApiUrl();
  const settings = await chrome.storage.sync.get(['githubToken', 'gitlabToken', 'snykToken']);
  
  // Build URL with query params
  const url = `${apiUrl}/api/v1/scan/stream${forceRescan ? '?force=true' : ''}`;
  
  return new Promise((resolve, reject) => {
    // Use fetch for POST with SSE-like behavior
    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        ...(settings.githubToken && { 'X-GitHub-Token': settings.githubToken }),
        ...(settings.gitlabToken && { 'X-GitLab-Token': settings.gitlabToken }),
        ...(settings.snykToken && { 'X-Snyk-Token': settings.snykToken }),
      },
      body: JSON.stringify(repository),
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const reader = response.body?.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        
        if (!reader) {
          reject(new Error('No response body'));
          return;
        }
        
        const processStream = async () => {
          let finalResult: ScanResult | null = null;
          let accumulatedResult: Partial<ScanResult> | null = null;
          
            // Set timeout to prevent hanging (90 seconds)
          const timeoutId = setTimeout(() => {
            reader.cancel().catch(() => {});
            if (finalResult) {
              resolve(finalResult);
            } else if (accumulatedResult) {
              // Use accumulated result if available
              resolve({
                repository: repository,
                secrets: accumulatedResult.secrets || [],
                vulnerabilities: accumulatedResult.vulnerabilities || [],
                outdatedDependencies: accumulatedResult.outdatedDependencies || [],
                securityScore: accumulatedResult.securityScore || 100,
                lastScanned: new Date().toISOString(),
                error: undefined,
              } as ScanResult);
            } else {
              reject(new Error('Scan timeout: No results received within 90 seconds'));
            }
          }, 90000);
          
          try {
            while (true) {
              const { done, value } = await reader.read();
              
              if (done) {
                clearTimeout(timeoutId);
                // Stream ended - try to use final result or accumulated data
                if (finalResult) {
                  console.log('Stream ended with final result');
                  resolve(finalResult);
                } else if (accumulatedResult) {
                  // Use accumulated result if we have partial data
                  console.warn('Stream ended without complete event, using accumulated data');
                  const accumulatedScanResult = {
                    repository: repository,
                    secrets: accumulatedResult.secrets || [],
                    vulnerabilities: accumulatedResult.vulnerabilities || [],
                    outdatedDependencies: accumulatedResult.outdatedDependencies || [],
                    securityScore: accumulatedResult.securityScore || 100,
                    lastScanned: new Date().toISOString(),
                    error: undefined,
                  } as ScanResult;
                  // Resolve with accumulated result - this will be handled by frontend
                  resolve(accumulatedScanResult);
                } else {
                  // No data at all - this is a real error
                  reject(new Error('Stream ended without complete event or any data'));
                }
                break;
              }
              
              buffer += decoder.decode(value, { stream: true });
              
              // Process complete SSE events (separated by \n\n)
              let eventEnd;
              while ((eventEnd = buffer.indexOf('\n\n')) !== -1) {
                const event = buffer.substring(0, eventEnd);
                buffer = buffer.substring(eventEnd + 2);
                
                if (!event.trim()) continue;
                
                // Parse SSE format: event: type\ndata: {...}
                let dataStr = '';
                
                for (const line of event.split('\n')) {
                  if (line.startsWith('data: ')) {
                    dataStr = line.substring(6).trim();
                    break; // Found data, no need to continue
                  }
                }
                
                if (dataStr) {
                  try {
                    const data: ScanProgress = JSON.parse(dataStr);
                    
                    // Accumulate partial results
                    if (data.secrets) {
                      accumulatedResult = { ...(accumulatedResult || {}), secrets: data.secrets };
                    }
                    if (data.vulnerabilities) {
                      accumulatedResult = { ...(accumulatedResult || {}), vulnerabilities: data.vulnerabilities };
                    }
                    if (data.outdatedDependencies) {
                      accumulatedResult = { ...(accumulatedResult || {}), outdatedDependencies: data.outdatedDependencies };
                    }
                    
                    // Call onProgress FIRST to update UI immediately
                    onProgress(data);
                    
                    // Then handle completion/error
                    if (data.type === 'complete') {
                      clearTimeout(timeoutId);
                      // Store final result
                      if (data.finalResult) {
                        finalResult = data.finalResult;
                      } else if (accumulatedResult) {
                        // If no finalResult but we have accumulated data, create result
                        finalResult = {
                          repository: repository,
                          secrets: accumulatedResult.secrets || [],
                          vulnerabilities: accumulatedResult.vulnerabilities || [],
                          outdatedDependencies: accumulatedResult.outdatedDependencies || [],
                          securityScore: accumulatedResult.securityScore || 100,
                          lastScanned: new Date().toISOString(),
                          error: undefined,
                        } as ScanResult;
                      }
                      // Cancel reader to close connection immediately
                      reader.cancel().catch(() => {});
                      // Resolve with finalResult
                      resolve(finalResult || {} as ScanResult);
                      return;
                    } else if (data.type === 'error') {
                      clearTimeout(timeoutId);
                      reader.cancel().catch(() => {});
                      reject(new Error(data.message || 'Scan failed'));
                      return;
                    }
                  } catch (e) {
                    console.error('Error parsing SSE data:', e, 'Raw data:', dataStr);
                  }
                }
              }
            }
          } catch (error: any) {
            clearTimeout(timeoutId);
            // If error is not cancellation, reject
            if (error.name !== 'AbortError' && error.message !== 'The operation was aborted') {
              // Try to use accumulated result before rejecting
              if (accumulatedResult && !finalResult) {
                console.warn('Error occurred but using accumulated data:', error);
                resolve({
                  repository: repository,
                  secrets: accumulatedResult.secrets || [],
                  vulnerabilities: accumulatedResult.vulnerabilities || [],
                  outdatedDependencies: accumulatedResult.outdatedDependencies || [],
                  securityScore: accumulatedResult.securityScore || 100,
                  lastScanned: new Date().toISOString(),
                  error: undefined,
                } as ScanResult);
              } else if (finalResult) {
                resolve(finalResult);
              } else {
                reject(error);
              }
            } else if (finalResult) {
              // If cancelled but we have result, resolve with it
              resolve(finalResult);
            } else if (accumulatedResult) {
              resolve({
                repository: repository,
                secrets: accumulatedResult.secrets || [],
                vulnerabilities: accumulatedResult.vulnerabilities || [],
                outdatedDependencies: accumulatedResult.outdatedDependencies || [],
                securityScore: accumulatedResult.securityScore || 100,
                lastScanned: new Date().toISOString(),
                error: undefined,
              } as ScanResult);
            }
          }
        };
        
        processStream();
      })
      .catch(reject);
  });
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
