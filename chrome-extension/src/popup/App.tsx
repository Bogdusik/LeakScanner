import { useState, useEffect } from 'react';
import { Shield, AlertTriangle, CheckCircle, Loader2, RefreshCw, Settings, ExternalLink, Package } from 'lucide-react';
import { useScanStore } from '../store/scanStore';
import { scanRepositoryStream } from '../services/api';
import { ScanResult, ScanProgress } from '../types';
import ScanResults from './components/ScanResults';
import SettingsPanel from './components/SettingsPanel';
import { getCurrentRepository } from '../utils/repository';
import { Repository } from '../types';

const App: React.FC = () => {
  // Use selector to ensure component re-renders on state changes
  const scanResult = useScanStore((state) => state.scanResult);
  const isLoading = useScanStore((state) => state.isLoading);
  const setScanResult = useScanStore((state) => state.setScanResult);
  const setLoading = useScanStore((state) => state.setLoading);
  const [showSettings, setShowSettings] = useState(false);
  const [currentRepo, setCurrentRepo] = useState<Repository | null>(null);
  const [hasTokens, setHasTokens] = useState<{github: boolean, gitlab: boolean}>({github: false, gitlab: false});
  const [scanProgress, setScanProgress] = useState<{ percent: number; status: string; estimatedTimeRemaining?: number }>({
    percent: 0,
    status: 'Idle',
  });
  const [progressTimer, setProgressTimer] = useState<NodeJS.Timeout | null>(null);

  useEffect(() => {
    loadCurrentRepository();
    checkTokens();
    autoSetupToken(); // Auto-setup token on first launch
  }, []);

  const autoSetupToken = async () => {
    // Check if token already exists
    const settings = await chrome.storage.sync.get('githubToken');
    if (!settings.githubToken) {
      // Token should be set manually via Settings panel
      // Auto-setup removed for security
    }
  };

  const checkTokens = async () => {
    const settings = await chrome.storage.sync.get(['githubToken', 'gitlabToken']);
    setHasTokens({
      github: !!settings.githubToken,
      gitlab: !!settings.gitlabToken,
    });
  };

  const loadCurrentRepository = async () => {
    // First try to get from storage (set by content script)
    const stored = await chrome.storage.local.get('currentRepository');
    if (stored.currentRepository) {
      setCurrentRepo(stored.currentRepository);
      loadCachedResult(stored.currentRepository);
      // Clear stored repo after use
      chrome.storage.local.remove('currentRepository');
      return;
    }
    
    // Fallback to URL parsing
    const repo = await getCurrentRepository();
    setCurrentRepo(repo);
    if (repo) {
      loadCachedResult(repo);
    }
  };

  const loadCachedResult = async (repo: Repository) => {
    const cacheKey = `${repo.platform}:${repo.owner}/${repo.name}`;
    const cached = await chrome.storage.local.get(cacheKey);
    if (cached[cacheKey]) {
      setScanResult(cached[cacheKey]);
      // Reset progress to 100% if we have a cached result (scan was completed)
      setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
    } else {
      // Reset progress if no cached result
      setScanProgress({ percent: 0, status: 'Idle' });
    }
  };

  const handleScan = async () => {
    if (!currentRepo) {
      return;
    }

    // Prevent multiple simultaneous scans
    if (isLoading) {
      return;
    }
    
    // Set loading state immediately and synchronously
    setLoading(true);
    
    startProgress();
    let scanCompleted = false;
    
    try {
      setScanProgress({ percent: 5, status: 'Starting scan...' });
      
      // Initialize result state
      let currentResult: ScanResult = {
        repository: currentRepo,
        secrets: [],
        vulnerabilities: [],
        outdatedDependencies: [],
        securityScore: 100,
        lastScanned: new Date().toISOString(),
      };
      setScanResult(currentResult);
      
      // Record scan start time for estimated time calculation
      const startTime = Date.now();
      
      // Use streaming scan to get results in real-time
      const result = await scanRepositoryStream(
        currentRepo,
        (progress: ScanProgress) => {
          // Calculate estimated time remaining
          const calculateEstimatedTime = (currentPercent: number): number | undefined => {
            if (currentPercent <= 0 || currentPercent >= 100) return undefined;
            const elapsed = Date.now() - startTime;
            const estimatedTotal = (elapsed / currentPercent) * 100;
            const remaining = estimatedTotal - elapsed;
            return Math.max(0, Math.round(remaining / 1000)); // Return in seconds
          };
          
          // Handle complete event FIRST to ensure immediate UI update
          if (progress.type === 'complete') {
            // Immediately update to 100% and show final result
            console.log('Received complete event, updating UI to 100%');
            setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
            if (progress.finalResult) {
              setScanResult(progress.finalResult);
              currentResult = progress.finalResult;
              // Cache immediately
              const cacheKey = `${currentRepo.platform}:${currentRepo.owner}/${currentRepo.name}`;
              chrome.storage.local.set({ [cacheKey]: progress.finalResult }).catch(console.error);
            } else {
              // If no finalResult, use accumulated result
              setScanResult(currentResult);
            }
            // Stop progress timer immediately
            stopProgress(true);
            return; // Exit early to prevent other handlers
          }
          
          // Update progress for all other events
          if (progress.progress !== undefined) {
            const estimatedTime = calculateEstimatedTime(progress.progress);
            setScanProgress({ 
              percent: progress.progress, 
              status: progress.status || 'Scanning...',
              estimatedTimeRemaining: estimatedTime
            });
          } else if (progress.status) {
            // Update status even if progress is not provided
            setScanProgress(prev => ({ 
              ...prev,
              status: progress.status || prev.status
            }));
          }
          
          // Update results as they come in - merge with existing results
          if (progress.type === 'secrets' && progress.secrets) {
            currentResult = {
              ...currentResult,
              secrets: progress.secrets,
            };
            setScanResult({ ...currentResult });
          }
          
          if (progress.type === 'vulnerabilities' && progress.vulnerabilities) {
            currentResult = {
              ...currentResult,
              vulnerabilities: progress.vulnerabilities,
            };
            setScanResult({ ...currentResult });
          }
          
          if (progress.type === 'dependencies' && progress.outdatedDependencies) {
            currentResult = {
              ...currentResult,
              outdatedDependencies: progress.outdatedDependencies,
            };
            setScanResult({ ...currentResult });
          }
          
          // Update progress status for progress events
          if (progress.type === 'progress' && progress.status) {
            setScanProgress({ 
              percent: progress.progress || 0, 
              status: progress.status 
            });
          }
        },
        true // Always force rescan
      ).catch((error: any) => {
        // If we get an error but have accumulated results, try to use them
        console.warn('Stream error, but may have partial results:', error);
        // Return accumulated result if available, otherwise throw
        if (currentResult && (currentResult.secrets.length > 0 || currentResult.vulnerabilities.length > 0 || currentResult.outdatedDependencies.length > 0)) {
          console.log('Using accumulated result despite error');
          // Update UI with accumulated result
          setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
          setScanResult(currentResult);
          scanCompleted = true;
          // Cache the result
          const cacheKey = `${currentRepo.platform}:${currentRepo.owner}/${currentRepo.name}`;
          chrome.storage.local.set({ [cacheKey]: currentResult }).catch(console.error);
          return currentResult;
        }
        throw error;
      });
      
      // Ensure progress reaches 100% and result is set when promise resolves
      // (This is a safety net - should already be set by complete event handler)
      // But we ensure it's set here in case the event handler didn't fire
      if (result) {
        setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
        setScanResult(result);
        scanCompleted = true;
      }
      
      // Cache result immediately so it's available even if user closes extension
      const cacheKey = `${currentRepo.platform}:${currentRepo.owner}/${currentRepo.name}`;
      await chrome.storage.local.set({ [cacheKey]: result });
      
      console.log('Scan completed and cached:', cacheKey);
    } catch (error: any) {
      console.error('Scan error:', error);
      
      // Check if we have cached result to use instead
      const cacheKey = `${currentRepo.platform}:${currentRepo.owner}/${currentRepo.name}`;
      const cached = await chrome.storage.local.get(cacheKey);
      
      if (cached[cacheKey] && !error.message?.includes('Stream ended without complete event or any data')) {
        // Use cached result if available and error is not about missing data
        console.log('Using cached result after error');
        setScanResult(cached[cacheKey]);
        setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
        scanCompleted = true;
        return;
      }
      
      let errorMessage = 'Failed to scan repository. ';
      
      if (error.code === 'ECONNREFUSED' || error.message?.includes('Network Error')) {
        const settings = await chrome.storage.sync.get('apiUrl');
        errorMessage += `Cannot connect to backend API. Please check if the server is running at ${settings.apiUrl || 'http://localhost:8080'}`;
      } else if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        errorMessage += 'Request timed out. The scan is taking too long. Please try again or check your network connection.';
      } else if (error.message?.includes('Stream ended without complete event')) {
        // Special handling for stream errors - try to use cached result
        if (cached[cacheKey]) {
          console.log('Stream ended but using cached result');
          setScanResult(cached[cacheKey]);
          setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
          scanCompleted = true;
          return;
        }
        errorMessage += 'Scan stream ended unexpectedly. Results may be incomplete.';
      } else if (error.response) {
        errorMessage += `Server error: ${error.response.status} - ${error.response.data?.message || error.response.statusText}`;
      } else if (error.message) {
        errorMessage += error.message;
      } else {
        errorMessage += 'Unknown error occurred. Please check the console for details.';
      }
      
      setScanResult({
        repository: currentRepo,
        secrets: [],
        vulnerabilities: [],
        outdatedDependencies: [],
        securityScore: 0,
        lastScanned: new Date().toISOString(),
        error: errorMessage,
      });
      setScanProgress({ percent: 100, status: 'Error', estimatedTimeRemaining: undefined });
      scanCompleted = true;
    } finally {
      setLoading(false);
      // Always stop progress and ensure it's at 100% if completed
      if (scanCompleted) {
        stopProgress(true);
      } else {
        // If somehow we got here without completion, force it
        setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
        stopProgress(true);
      }
    }
  };

  const startProgress = () => {
    if (progressTimer) {
      clearInterval(progressTimer);
      setProgressTimer(null);
    }
    const startTime = Date.now();
    setScanProgress({ percent: 5, status: 'Collecting repository metadata...' });
    let currentPercent = 5;
    const timer = setInterval(() => {
      setScanProgress((prev) => {
        // Gradually increase progress, but cap at 90% to leave room for real progress updates
        // Real progress updates from backend will override this
        // Don't update if already at 100% (completed)
        if (prev.percent >= 100) {
          return prev; // Keep at 100% once completed
        }
        currentPercent = Math.min(currentPercent + Math.random() * 2, 90);
        let status = prev.status;
        // Only update status if it hasn't been updated by real progress from backend
        // Backend sends: 5%, 10%, 20%, 40%, 50%, 70%, 80%, 90%, 92%, 95%, 98%, 100%
        if (!prev.status.includes('Calculating') && !prev.status.includes('Saving') && !prev.status.includes('Finalizing') && !prev.status.includes('Completed')) {
          if (currentPercent < 30) status = 'Scanning secrets...';
          else if (currentPercent < 60) status = 'Analyzing vulnerabilities...';
          else if (currentPercent < 85) status = 'Checking dependencies...';
          else status = 'Finalizing...';
        } else {
          status = prev.status; // Keep real status from backend
        }
        
        // Calculate estimated time remaining using startTime from closure
        let estimatedTime: number | undefined = undefined;
        if (currentPercent > 0 && currentPercent < 100) {
          const elapsed = Date.now() - startTime;
          const estimatedTotal = (elapsed / currentPercent) * 100;
          const remaining = estimatedTotal - elapsed;
          estimatedTime = Math.max(0, Math.round(remaining / 1000)); // Return in seconds
        }
        
        return { percent: currentPercent, status, estimatedTimeRemaining: estimatedTime };
      });
    }, 500);
    setProgressTimer(timer);
    
    // Safety timeout: if scan takes too long, force completion (50 seconds)
    setTimeout(() => {
      // Check if this timer is still the active one by using functional update
      setProgressTimer((currentTimer) => {
        if (currentTimer === timer) {
          setScanProgress({ percent: 100, status: 'Completed', estimatedTimeRemaining: undefined });
          clearInterval(timer);
          stopProgress(true);
        }
        return currentTimer;
      });
    }, 50000); // 50 seconds (slightly more than backend timeout of 45s)
  };

  const stopProgress = (instant = false) => {
    if (progressTimer) clearInterval(progressTimer);
    setProgressTimer(null);
    if (instant) {
      setScanProgress((prev) => ({ ...prev, percent: 100, estimatedTimeRemaining: undefined }));
      setTimeout(() => {
        setScanProgress({ percent: 0, status: 'Idle' });
      }, 900);
    }
  };
  
  const formatTimeRemaining = (seconds: number | undefined): string => {
    if (seconds === undefined || seconds <= 0) return '';
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return secs > 0 ? `${minutes}m ${secs}s` : `${minutes}m`;
  };

  const getSecurityScoreColor = (score: number) => {
    if (score >= 80) return 'text-rog-cyan rog-text-gradient';
    if (score >= 60) return 'text-rog-purple';
    return 'text-rog-pink';
  };

  const getSecurityScoreIcon = (score: number) => {
    if (score >= 80) return <CheckCircle className="w-4 h-4 text-rog-cyan rog-glow-cyan" />;
    if (score >= 60) return <AlertTriangle className="w-4 h-4 text-rog-purple rog-glow-purple" />;
    return <AlertTriangle className="w-4 h-4 text-rog-pink rog-glow-pink" />;
  };

  return (
    <div className="w-[420px] min-h-[450px] bg-rog-dark relative overflow-hidden border border-rog-gray">
      {/* ROG RGB Background Effects */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-0 left-0 w-96 h-96 bg-rog-cyan opacity-10 rounded-full blur-3xl animate-blob"></div>
        <div className="absolute top-0 right-0 w-96 h-96 bg-rog-purple opacity-10 rounded-full blur-3xl animate-blob animation-delay-2000"></div>
        <div className="absolute bottom-0 left-1/2 w-96 h-96 bg-rog-pink opacity-10 rounded-full blur-3xl animate-blob animation-delay-4000"></div>
      </div>
      
      {/* Header - ROG Style */}
      <div className="relative bg-rog-darkGray border-b border-rog-cyan/30 rog-border-glow p-2.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1 bg-rog-gray rounded-lg rog-glow-cyan">
              <Shield className="w-4 h-4 text-rog-cyan" />
            </div>
            <h1 className="text-lg font-bold tracking-tight rog-text-gradient">LeakScanner</h1>
          </div>
          <button
            onClick={() => setShowSettings(!showSettings)}
            className="p-1 hover:bg-rog-gray rounded-lg transition-all duration-200 hover:scale-110 rog-glow-cyan"
            title="Settings"
          >
            <Settings className="w-3.5 h-3.5 text-rog-cyan" />
          </button>
        </div>
      </div>

      {/* Settings Panel */}
      {showSettings && (
        <SettingsPanel 
          onClose={() => {
            setShowSettings(false);
            checkTokens(); // Refresh token status after closing settings
          }} 
        />
      )}

      {/* Main Content */}
      <div className="relative p-2.5 space-y-2">
        {currentRepo ? (
          <>
            {isLoading && scanProgress.percent > 0 && (
              <div className="p-2 bg-rog-darkGray border border-rog-cyan/30 rounded-lg rog-glow-cyan">
                <div className="flex items-center gap-1.5 mb-1.5">
                  <div className="w-5 h-5 rounded bg-gradient-to-br from-rog-cyan to-rog-purple flex items-center justify-center rog-glow">
                    <Loader2 className="w-3 h-3 text-white animate-spin" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold text-rog-cyan truncate">Scanning...</p>
                    <div className="flex items-center gap-1.5">
                      <p className="text-xs text-gray-300 leading-tight truncate">{scanProgress.status}</p>
                      {scanProgress.estimatedTimeRemaining !== undefined && scanProgress.estimatedTimeRemaining > 0 && (
                        <span className="text-xs text-rog-purple font-medium whitespace-nowrap">
                          • ~{formatTimeRemaining(scanProgress.estimatedTimeRemaining)}
                        </span>
                      )}
                    </div>
                  </div>
                  <span className="text-xs font-bold text-rog-cyan bg-rog-gray px-1.5 py-0.5 rounded rog-glow-cyan whitespace-nowrap">
                    {Math.round(scanProgress.percent)}%
                  </span>
                </div>
                <div className="w-full bg-rog-darker rounded-full h-1.5 overflow-hidden">
                  <div
                    className="h-1.5 rounded-full bg-gradient-to-r from-rog-cyan via-rog-purple to-rog-pink transition-all duration-300 ease-out rog-glow"
                    style={{ width: `${scanProgress.percent}%` }}
                  />
                </div>
              </div>
            )}

            {/* Repository Info - ROG Style */}
            <div className="p-2 bg-rog-darkGray border border-rog-cyan/30 rounded-lg rog-glow-cyan hover:rog-glow transition-all duration-200">
              <div className="flex items-center justify-between gap-2">
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1 leading-tight">Repository</p>
                  <p className="font-bold text-white text-sm mb-1 truncate leading-tight">
                    {currentRepo.owner}/{currentRepo.name}
                  </p>
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-rog-gray text-rog-cyan capitalize">
                    {currentRepo.platform}
                  </span>
                </div>
                <a
                  href={`https://${currentRepo.platform === 'github' ? 'github.com' : 'gitlab.com'}/${currentRepo.owner}/${currentRepo.name}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="p-1 text-rog-cyan hover:text-rog-cyanDark hover:bg-rog-gray rounded transition-all duration-200 rog-glow-cyan flex-shrink-0"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>

            {/* Token Warning - ROG Style */}
            {(!hasTokens.github && currentRepo.platform === 'github') || 
             (!hasTokens.gitlab && currentRepo.platform === 'gitlab') ? (
              <div className="p-2 bg-rog-darkGray border border-rog-purple/30 rounded-lg rog-glow-purple">
                <div className="flex items-start gap-1.5">
                  <AlertTriangle className="w-3 h-3 text-rog-purple flex-shrink-0 mt-0.5" />
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-bold text-rog-purple mb-0.5 leading-tight">
                      Token Recommended
                    </p>
                    <p className="text-xs text-gray-300 leading-tight">
                      Add {currentRepo.platform === 'github' ? 'GitHub' : 'GitLab'} token for private repos.
                    </p>
                  </div>
                  <button
                    onClick={() => setShowSettings(true)}
                    className="text-xs text-rog-purple hover:text-rog-purpleDark font-medium underline whitespace-nowrap flex-shrink-0"
                  >
                    Setup
                  </button>
                </div>
              </div>
            ) : null}

            {/* Scan Button - ROG RGB Style */}
            <button
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                handleScan();
              }}
              disabled={isLoading || !currentRepo}
              className="w-full bg-gradient-to-r from-rog-cyan via-rog-purple to-rog-pink hover:from-rog-cyanDark hover:via-rog-purpleDark hover:to-rog-pinkDark disabled:from-gray-600 disabled:via-gray-600 disabled:to-gray-600 text-white font-bold py-2 px-3 rounded-lg transition-all duration-200 flex items-center justify-center gap-1.5 disabled:cursor-not-allowed transform hover:scale-[1.02] active:scale-[0.98] text-xs uppercase tracking-wide"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Scanning...</span>
                </>
              ) : (
                <>
                  <RefreshCw className="w-4 h-4" />
                  <span>Scan Repository</span>
                </>
              )}
            </button>

            {/* Security Score - ROG Style with animation */}
            {scanResult ? (
              <div className="p-2 bg-rog-darkGray border border-rog-cyan/30 rounded-lg rog-glow-cyan animate-fadeIn">
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-xs font-semibold text-rog-cyan uppercase tracking-wide">Security Score</span>
                  {getSecurityScoreIcon(scanResult.securityScore)}
                </div>
                <div className="flex items-center gap-1.5 mb-1.5">
                  <div className="flex-1 bg-rog-darker rounded-full h-2 overflow-hidden">
                    <div
                      className={`h-2 rounded-full transition-all duration-500 ease-out rog-glow ${
                        scanResult.securityScore >= 80
                          ? 'bg-gradient-to-r from-rog-cyan to-green-500'
                          : scanResult.securityScore >= 60
                          ? 'bg-gradient-to-r from-rog-purple to-orange-500'
                          : 'bg-gradient-to-r from-red-500 to-rog-pink'
                      }`}
                      style={{ width: `${scanResult.securityScore}%` }}
                    />
                  </div>
                  <span className={`text-lg font-bold ${getSecurityScoreColor(scanResult.securityScore)}`}>
                    {scanResult.securityScore}
                  </span>
                </div>
                {scanResult.lastScanned && (
                  <p className="text-xs text-gray-300 flex items-center gap-1 leading-tight">
                    <span className="font-medium">Last scanned:</span>
                    <span className="font-semibold text-rog-cyan">{new Date(scanResult.lastScanned).toLocaleString()}</span>
                  </p>
                )}
              </div>
            ) : null}

            {/* Error Message - ROG Style */}
            {scanResult?.error && (
              <div className="p-2 bg-rog-darkGray border border-red-500/30 rounded-lg rog-glow-pink">
                <div className="flex items-start gap-1.5">
                  <AlertTriangle className="w-3 h-3 text-red-500 flex-shrink-0 mt-0.5" />
                  <p className="text-xs text-red-400 font-medium leading-tight">{scanResult.error}</p>
                </div>
              </div>
            )}

            {/* Scan Results - with animation */}
            {scanResult && !scanResult.error && (
              <div className="animate-slideUp">
                <ScanResults result={scanResult} />
              </div>
            )}

            {/* Empty State - Improved */}
            {!scanResult && !isLoading && (
              <div className="text-center py-8 animate-fadeIn">
                <div className="inline-flex items-center justify-center w-14 h-14 bg-rog-darkGray border-2 border-rog-cyan/40 rounded-xl mb-3 animate-pulse-slow">
                  <Shield className="w-7 h-7 text-rog-cyan" />
                </div>
                <h3 className="text-sm font-bold text-white mb-1.5 leading-tight">Ready to Scan</h3>
                <p className="text-xs font-medium text-gray-300 mb-1 leading-tight">Click "Scan Repository" to analyze security</p>
                <p className="text-xs text-gray-400 mt-1.5 leading-tight">Get instant insights on secrets, vulnerabilities, and dependencies</p>
                <div className="mt-3 flex items-center justify-center gap-1.5 text-xs text-gray-400">
                  <span className="flex items-center gap-1">
                    <Shield className="w-3 h-3 text-rog-cyan" />
                    Secrets
                  </span>
                  <span className="text-gray-600">•</span>
                  <span className="flex items-center gap-1">
                    <AlertTriangle className="w-3 h-3 text-rog-purple" />
                    Vulnerabilities
                  </span>
                  <span className="text-gray-600">•</span>
                  <span className="flex items-center gap-1">
                    <Package className="w-3 h-3 text-rog-cyan" />
                    Dependencies
                  </span>
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="text-center py-12 animate-fadeIn">
            <div className="inline-flex items-center justify-center w-16 h-16 bg-rog-darkGray border-2 border-rog-purple/40 rounded-xl mb-4">
              <AlertTriangle className="w-8 h-8 text-rog-purple" />
            </div>
            <h3 className="text-sm font-bold text-white mb-1.5 leading-tight">No Repository Detected</h3>
            <p className="text-xs font-medium text-gray-300 mb-1 leading-tight">Please navigate to a GitHub or GitLab repository</p>
            <p className="text-xs text-gray-400 mt-1.5 leading-tight">Open any repository page to start scanning</p>
            <div className="mt-3 flex items-center justify-center gap-2 text-xs">
              <a 
                href="https://github.com" 
                target="_blank" 
                rel="noopener noreferrer"
                className="px-3 py-1.5 bg-rog-gray hover:bg-rog-darkGray text-rog-cyan rounded-lg font-semibold transition-all duration-200 hover:scale-105"
              >
                Go to GitHub
              </a>
              <a 
                href="https://gitlab.com" 
                target="_blank" 
                rel="noopener noreferrer"
                className="px-3 py-1.5 bg-rog-gray hover:bg-rog-darkGray text-rog-purple rounded-lg font-semibold transition-all duration-200 hover:scale-105"
              >
                Go to GitLab
              </a>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default App;
