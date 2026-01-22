import { useState, useEffect } from 'react';
import { Shield, AlertTriangle, CheckCircle, Loader2, RefreshCw, Settings, ExternalLink, Package } from 'lucide-react';
import { useScanStore } from '../store/scanStore';
import { scanRepository } from '../services/api';
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
  const [hasTokens, setHasTokens] = useState<{github: boolean, gitlab: boolean, snyk: boolean}>({github: false, gitlab: false, snyk: false});
  const [scanProgress, setScanProgress] = useState<{ percent: number; status: string }>({
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
    const settings = await chrome.storage.sync.get(['githubToken', 'gitlabToken', 'snykToken']);
    setHasTokens({
      github: !!settings.githubToken,
      gitlab: !!settings.gitlabToken,
      snyk: !!settings.snykToken,
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
      setScanProgress({ percent: 5, status: 'Collecting repository metadata...' });
      const result = await scanRepository(currentRepo);
      
      // Ensure progress reaches 100% when result arrives
      setScanProgress({ percent: 100, status: 'Completed' });
      setScanResult(result);
      // Force a small delay to ensure state update
      await new Promise(resolve => setTimeout(resolve, 100));
      scanCompleted = true;
      
      // Cache result
      const cacheKey = `${currentRepo.platform}:${currentRepo.owner}/${currentRepo.name}`;
      await chrome.storage.local.set({ [cacheKey]: result });
    } catch (error: any) {
      console.error('Scan error:', error);
      
      let errorMessage = 'Failed to scan repository. ';
      
      if (error.code === 'ECONNREFUSED' || error.message?.includes('Network Error')) {
        const settings = await chrome.storage.sync.get('apiUrl');
        errorMessage += `Cannot connect to backend API. Please check if the server is running at ${settings.apiUrl || 'http://localhost:8080'}`;
      } else if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        errorMessage += 'Request timed out. The scan is taking too long. Please try again or check your network connection.';
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
      setScanProgress({ percent: 100, status: 'Error' });
      scanCompleted = true;
    } finally {
      setLoading(false);
      // Always stop progress and ensure it's at 100% if completed
      if (scanCompleted) {
        stopProgress(true);
      } else {
        // If somehow we got here without completion, force it
        setScanProgress({ percent: 100, status: 'Completed' });
        stopProgress(true);
      }
    }
  };

  const startProgress = () => {
    if (progressTimer) {
      clearInterval(progressTimer);
      setProgressTimer(null);
    }
    setScanProgress({ percent: 5, status: 'Collecting repository metadata...' });
    let currentPercent = 5;
    const timer = setInterval(() => {
      setScanProgress((prev) => {
        // Gradually increase progress, but cap at 95% to leave room for completion
        currentPercent = Math.min(currentPercent + Math.random() * 5, 95);
        let status = prev.status;
        if (currentPercent < 30) status = 'Scanning secrets...';
        else if (currentPercent < 60) status = 'Analyzing vulnerabilities...';
        else if (currentPercent < 85) status = 'Checking dependencies...';
        else status = 'Finalizing results...';
        return { percent: currentPercent, status };
      });
    }, 500);
    setProgressTimer(timer);
    
    // Safety timeout: if scan takes too long, force completion (50 seconds)
    setTimeout(() => {
      if (progressTimer === timer) {
        setScanProgress({ percent: 100, status: 'Completed' });
        stopProgress(true);
      }
    }, 50000); // 50 seconds (slightly more than backend timeout of 45s)
  };

  const stopProgress = (instant = false) => {
    if (progressTimer) clearInterval(progressTimer);
    setProgressTimer(null);
    if (instant) {
      setScanProgress((prev) => ({ ...prev, percent: 100 }));
      setTimeout(() => {
        setScanProgress({ percent: 0, status: 'Idle' });
      }, 900);
    }
  };

  const getSecurityScoreColor = (score: number) => {
    if (score >= 80) return 'text-rog-cyan rog-text-gradient';
    if (score >= 60) return 'text-rog-purple';
    return 'text-rog-pink';
  };

  const getSecurityScoreIcon = (score: number) => {
    if (score >= 80) return <CheckCircle className="w-5 h-5 text-rog-cyan rog-glow-cyan" />;
    if (score >= 60) return <AlertTriangle className="w-5 h-5 text-rog-purple rog-glow-purple" />;
    return <AlertTriangle className="w-5 h-5 text-rog-pink rog-glow-pink" />;
  };

  return (
    <div className="w-[420px] min-h-[500px] bg-rog-dark relative overflow-hidden border border-rog-gray">
      {/* ROG RGB Background Effects */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-0 left-0 w-96 h-96 bg-rog-cyan opacity-10 rounded-full blur-3xl animate-blob"></div>
        <div className="absolute top-0 right-0 w-96 h-96 bg-rog-purple opacity-10 rounded-full blur-3xl animate-blob animation-delay-2000"></div>
        <div className="absolute bottom-0 left-1/2 w-96 h-96 bg-rog-pink opacity-10 rounded-full blur-3xl animate-blob animation-delay-4000"></div>
      </div>
      
      {/* Header - ROG Style */}
      <div className="relative bg-rog-darkGray border-b border-rog-cyan/30 rog-border-glow p-3.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 bg-rog-gray rounded-lg rog-glow-cyan">
              <Shield className="w-5 h-5 text-rog-cyan" />
            </div>
            <h1 className="text-xl font-bold tracking-tight rog-text-gradient">LeakScanner</h1>
          </div>
          <button
            onClick={() => setShowSettings(!showSettings)}
            className="p-1.5 hover:bg-rog-gray rounded-lg transition-all duration-200 hover:scale-110 rog-glow-cyan"
            title="Settings"
          >
            <Settings className="w-4 h-4 text-rog-cyan" />
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
      <div className="relative p-3.5 space-y-3">
        {currentRepo ? (
          <>
            {isLoading && scanProgress.percent > 0 && (
              <div className="p-3 bg-rog-darkGray border border-rog-cyan/30 rounded-xl rog-glow-cyan">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-rog-cyan to-rog-purple flex items-center justify-center rog-glow">
                      <Loader2 className="w-4 h-4 text-white animate-spin" />
                    </div>
                  <div>
                    <p className="text-xs font-semibold text-rog-cyan">Scanning...</p>
                    <p className="text-xs text-gray-300 leading-relaxed">{scanProgress.status}</p>
                  </div>
                  </div>
                  <span className="text-xs font-bold text-rog-cyan bg-rog-gray px-2 py-1 rounded-lg rog-glow-cyan">{Math.round(scanProgress.percent)}%</span>
                </div>
                <div className="w-full bg-rog-darker rounded-full h-2 overflow-hidden">
                  <div
                    className="h-2 rounded-full bg-gradient-to-r from-rog-cyan via-rog-purple to-rog-pink transition-all duration-300 ease-out rog-glow"
                    style={{ width: `${scanProgress.percent}%` }}
                  />
                </div>
              </div>
            )}

            {/* Repository Info - ROG Style */}
            <div className="p-3 bg-rog-darkGray border border-rog-cyan/30 rounded-xl rog-glow-cyan hover:rog-glow transition-all duration-200">
              <div className="flex items-center justify-between">
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1.5 leading-tight">Repository</p>
                  <p className="font-bold text-white text-base mb-2 truncate leading-tight">
                    {currentRepo.owner}/{currentRepo.name}
                  </p>
                  <span className="inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-semibold bg-rog-gray text-rog-cyan capitalize">
                    {currentRepo.platform}
                  </span>
                </div>
                <a
                  href={`https://${currentRepo.platform === 'github' ? 'github.com' : 'gitlab.com'}/${currentRepo.owner}/${currentRepo.name}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="ml-2 p-1.5 text-rog-cyan hover:text-rog-cyanDark hover:bg-rog-gray rounded-lg transition-all duration-200 rog-glow-cyan"
                >
                  <ExternalLink className="w-4 h-4" />
                </a>
              </div>
            </div>

            {/* Token Warning - ROG Style */}
            {(!hasTokens.github && currentRepo.platform === 'github') || 
             (!hasTokens.gitlab && currentRepo.platform === 'gitlab') ? (
              <div className="p-2.5 bg-rog-darkGray border border-rog-purple/30 rounded-xl rog-glow-purple">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="w-3.5 h-3.5 text-rog-purple flex-shrink-0 mt-0.5" />
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-bold text-rog-purple mb-1 leading-tight">
                      Token Recommended
                    </p>
                    <p className="text-xs text-gray-300 leading-relaxed">
                      Add {currentRepo.platform === 'github' ? 'GitHub' : 'GitLab'} token for private repos.
                    </p>
                  </div>
                  <button
                    onClick={() => setShowSettings(true)}
                    className="text-xs text-rog-purple hover:text-rog-purpleDark font-medium underline whitespace-nowrap"
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
              className="w-full bg-gradient-to-r from-rog-cyan via-rog-purple to-rog-pink hover:from-rog-cyanDark hover:via-rog-purpleDark hover:to-rog-pinkDark disabled:from-gray-600 disabled:via-gray-600 disabled:to-gray-600 text-white font-bold py-2.5 px-4 rounded-xl transition-all duration-200 flex items-center justify-center gap-2 disabled:cursor-not-allowed transform hover:scale-[1.02] active:scale-[0.98] text-sm uppercase tracking-wide"
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
              <div className="p-3 bg-rog-darkGray border border-rog-cyan/30 rounded-xl rog-glow-cyan animate-fadeIn">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-semibold text-rog-cyan uppercase tracking-wide">Security Score</span>
                  {getSecurityScoreIcon(scanResult.securityScore)}
                </div>
                <div className="flex items-center gap-2 mb-2">
                  <div className="flex-1 bg-rog-darker rounded-full h-2.5 overflow-hidden">
                    <div
                      className={`h-2.5 rounded-full transition-all duration-500 ease-out rog-glow ${
                        scanResult.securityScore >= 80
                          ? 'bg-gradient-to-r from-rog-cyan to-green-500'
                          : scanResult.securityScore >= 60
                          ? 'bg-gradient-to-r from-rog-purple to-orange-500'
                          : 'bg-gradient-to-r from-red-500 to-rog-pink'
                      }`}
                      style={{ width: `${scanResult.securityScore}%` }}
                    />
                  </div>
                  <span className={`text-xl font-bold ${getSecurityScoreColor(scanResult.securityScore)}`}>
                    {scanResult.securityScore}
                  </span>
                </div>
                {scanResult.lastScanned && (
                  <p className="text-xs text-gray-300 flex items-center gap-1.5 leading-relaxed">
                    <span className="font-medium">Last scanned:</span>
                    <span className="font-semibold text-rog-cyan">{new Date(scanResult.lastScanned).toLocaleString()}</span>
                  </p>
                )}
              </div>
            ) : null}

            {/* Error Message - ROG Style */}
            {scanResult?.error && (
              <div className="p-2.5 bg-rog-darkGray border border-red-500/30 rounded-xl rog-glow-pink">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="w-3.5 h-3.5 text-red-500 flex-shrink-0 mt-0.5" />
                  <p className="text-xs text-red-400 font-medium">{scanResult.error}</p>
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
              <div className="text-center py-10 animate-fadeIn">
                <div className="inline-flex items-center justify-center w-16 h-16 bg-rog-darkGray border-2 border-rog-cyan/40 rounded-2xl mb-4 animate-pulse-slow">
                  <Shield className="w-8 h-8 text-rog-cyan" />
                </div>
                <h3 className="text-base font-bold text-white mb-2 leading-tight">Ready to Scan</h3>
                <p className="text-sm font-medium text-gray-300 mb-1 leading-relaxed">Click "Scan Repository" to analyze security</p>
                <p className="text-xs text-gray-400 mt-2 leading-relaxed">Get instant insights on secrets, vulnerabilities, and dependencies</p>
                <div className="mt-4 flex items-center justify-center gap-2 text-xs text-gray-400">
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
          <div className="text-center py-16 animate-fadeIn">
            <div className="inline-flex items-center justify-center w-20 h-20 bg-rog-darkGray border-2 border-rog-purple/40 rounded-2xl mb-5">
              <AlertTriangle className="w-10 h-10 text-rog-purple" />
            </div>
            <h3 className="text-base font-bold text-white mb-2 leading-tight">No Repository Detected</h3>
            <p className="text-sm font-medium text-gray-300 mb-1 leading-relaxed">Please navigate to a GitHub or GitLab repository</p>
            <p className="text-xs text-gray-400 mt-2 leading-relaxed">Open any repository page to start scanning</p>
            <div className="mt-5 flex items-center justify-center gap-3 text-xs">
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
