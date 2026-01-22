import React, { useEffect } from 'react';
import { AlertTriangle, Key, Package, Shield, ChevronDown, ChevronUp } from 'lucide-react';
import { ScanResult } from '../../types';

interface ScanResultsProps {
  result: ScanResult;
}

const ScanResults: React.FC<ScanResultsProps> = ({ result }) => {
  // All sections are closed by default
  const [expandedSections, setExpandedSections] = React.useState({
    secrets: false,
    vulnerabilities: false,
    dependencies: false,
  });

  // Automatically open only sections with issues
  useEffect(() => {
    setExpandedSections({
      secrets: result.secrets.length > 0,
      vulnerabilities: result.vulnerabilities.length > 0,
      dependencies: result.outdatedDependencies.length > 0,
    });
  }, [result.secrets.length, result.vulnerabilities.length, result.outdatedDependencies.length]);

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections((prev) => ({
      ...prev,
      [section]: !prev[section],
    }));
  };

  const getSeverityColor = (severity: string) => {
    switch (severity.toLowerCase()) {
      case 'critical':
      case 'high':
        return 'bg-rog-darker text-rog-pink border-rog-pink/50 rog-glow-pink';
      case 'medium':
        return 'bg-rog-darker text-rog-purple border-rog-purple/50 rog-glow-purple';
      case 'low':
        return 'bg-rog-darker text-rog-cyan border-rog-cyan/50 rog-glow-cyan';
      default:
        return 'bg-rog-darker text-gray-400 border-rog-gray';
    }
  };

  return (
    <div className="space-y-2">
      {/* Secrets Section - ROG Style */}
      <div className="bg-rog-darkGray border border-rog-pink/30 rounded-lg overflow-hidden rog-glow-pink hover:rog-glow transition-all duration-200">
        <button
          onClick={() => toggleSection('secrets')}
          className="w-full p-2 bg-rog-gray hover:bg-rog-darkGray flex items-center justify-between transition-all duration-200"
        >
          <div className="flex items-center gap-1.5">
            <div className="p-1 bg-rog-gray rounded rog-glow-pink">
              <Key className="w-3.5 h-3.5 text-rog-pink" />
            </div>
            <span className="font-bold text-white text-xs leading-tight">Secret Leaks</span>
            <span className="px-1.5 py-0.5 bg-rog-gray text-rog-pink rounded text-xs font-bold rog-glow-pink">
              {result.secrets.length}
            </span>
          </div>
          {expandedSections.secrets ? (
            <ChevronUp className="w-4 h-4 text-rog-pink" />
          ) : (
            <ChevronDown className="w-4 h-4 text-rog-pink" />
          )}
        </button>
        {expandedSections.secrets && (
          <div className="p-2 space-y-1.5">
            {result.secrets.length === 0 ? (
              <div className="text-center py-4 animate-fadeIn">
                <div className="inline-flex items-center justify-center w-10 h-10 bg-rog-darkGray border-2 border-rog-cyan/40 rounded-lg mb-2">
                  <Shield className="w-5 h-5 text-rog-cyan" />
                </div>
                <h4 className="text-xs font-bold text-white mb-0.5 leading-tight">No secrets found</h4>
                <p className="text-xs text-gray-300 leading-tight">Your repository is secure from secret leaks</p>
              </div>
            ) : (
              result.secrets.map((secret, index) => (
                <div
                  key={index}
                  className="p-2 bg-rog-darker border border-rog-pink/30 rounded-lg text-xs animate-fadeIn"
                  style={{ animationDelay: `${index * 0.05}s` }}
                >
                  <div className="flex items-start justify-between gap-1.5">
                    <div className="flex-1 min-w-0">
                      <p className="font-bold text-rog-pink mb-0.5 truncate text-xs leading-tight">{secret.type}</p>
                      <p className="text-gray-200 font-semibold text-xs truncate leading-tight">📄 {secret.file}</p>
                      <p className="text-gray-300 text-xs mt-0.5 leading-tight">📍 Line {secret.line}</p>
                    </div>
                    <span className={`px-1.5 py-0.5 rounded text-xs font-bold border ${getSeverityColor(secret.severity)} whitespace-nowrap`}>
                      {secret.severity.toUpperCase()}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* Vulnerabilities Section - ROG Style */}
      <div className="bg-rog-darkGray border border-rog-purple/30 rounded-lg overflow-hidden rog-glow-purple hover:rog-glow transition-all duration-200">
        <button
          onClick={() => toggleSection('vulnerabilities')}
          className="w-full p-2 bg-rog-gray hover:bg-rog-darkGray flex items-center justify-between transition-all duration-200"
        >
          <div className="flex items-center gap-1.5">
            <div className="p-1 bg-rog-gray rounded rog-glow-purple">
              <Shield className="w-3.5 h-3.5 text-rog-purple" />
            </div>
            <span className="font-bold text-white text-xs leading-tight">Vulnerabilities</span>
            <span className="px-1.5 py-0.5 bg-rog-gray text-rog-purple rounded text-xs font-bold rog-glow-purple">
              {result.vulnerabilities.length}
            </span>
          </div>
          {expandedSections.vulnerabilities ? (
            <ChevronUp className="w-4 h-4 text-rog-purple" />
          ) : (
            <ChevronDown className="w-4 h-4 text-rog-purple" />
          )}
        </button>
        {expandedSections.vulnerabilities && (
          <div className="p-2 space-y-1.5">
            {result.vulnerabilities.length === 0 ? (
              <div className="text-center py-4 animate-fadeIn">
                <div className="inline-flex items-center justify-center w-10 h-10 bg-rog-darkGray border-2 border-rog-purple/40 rounded-lg mb-2">
                  <Shield className="w-5 h-5 text-rog-purple" />
                </div>
                <h4 className="text-xs font-bold text-white mb-0.5 leading-tight">No vulnerabilities found</h4>
                <p className="text-xs text-gray-300 leading-tight">Your code is secure from known vulnerabilities</p>
              </div>
            ) : (
              result.vulnerabilities.map((vuln, index) => (
                <div
                  key={index}
                  className="p-2 bg-rog-darker border border-rog-purple/30 rounded-lg text-xs animate-fadeIn"
                  style={{ animationDelay: `${index * 0.05}s` }}
                >
                  <div className="flex items-start justify-between gap-1.5">
                    <div className="flex-1 min-w-0">
                      <p className="font-bold text-rog-purple mb-0.5 truncate text-xs leading-tight">{vuln.title}</p>
                      <p className="text-gray-200 text-xs mb-1 line-clamp-2 leading-tight">{vuln.description}</p>
                      {vuln.package && (
                        <p className="text-gray-300 text-xs font-semibold truncate leading-tight">📦 {vuln.package}</p>
                      )}
                      {vuln.cve && (
                        <p className="text-gray-300 text-xs font-semibold mt-0.5 leading-tight">🔒 CVE: {vuln.cve}</p>
                      )}
                    </div>
                    <span className={`px-1.5 py-0.5 rounded text-xs font-bold border ${getSeverityColor(vuln.severity)} whitespace-nowrap`}>
                      {vuln.severity.toUpperCase()}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* Outdated Dependencies Section - ROG Style */}
      <div className="bg-rog-darkGray border border-rog-cyan/30 rounded-lg overflow-hidden rog-glow-cyan hover:rog-glow transition-all duration-200">
        <button
          onClick={() => toggleSection('dependencies')}
          className="w-full p-2 bg-rog-gray hover:bg-rog-darkGray flex items-center justify-between transition-all duration-200"
        >
          <div className="flex items-center gap-1.5">
            <div className="p-1 bg-rog-gray rounded rog-glow-cyan">
              <Package className="w-3.5 h-3.5 text-rog-cyan" />
            </div>
            <span className="font-bold text-white text-xs leading-tight">Outdated Dependencies</span>
            <span className="px-1.5 py-0.5 bg-rog-gray text-rog-cyan rounded text-xs font-bold rog-glow-cyan">
              {result.outdatedDependencies.length}
            </span>
          </div>
          {expandedSections.dependencies ? (
            <ChevronUp className="w-4 h-4 text-rog-cyan" />
          ) : (
            <ChevronDown className="w-4 h-4 text-rog-cyan" />
          )}
        </button>
        {expandedSections.dependencies && (
          <div className="p-2 space-y-1.5">
            {result.outdatedDependencies.length === 0 ? (
              <div className="text-center py-4 animate-fadeIn">
                <div className="inline-flex items-center justify-center w-10 h-10 bg-rog-darkGray border-2 border-rog-cyan/40 rounded-lg mb-2">
                  <Package className="w-5 h-5 text-rog-cyan" />
                </div>
                <h4 className="text-xs font-bold text-white mb-0.5 leading-tight">All dependencies up to date</h4>
                <p className="text-xs text-gray-300 leading-tight">Your project is using the latest versions</p>
              </div>
            ) : (
              result.outdatedDependencies.map((dep, index) => (
                <div
                  key={index}
                  className="p-2 bg-rog-darker border border-rog-cyan/30 rounded-lg text-xs animate-fadeIn"
                  style={{ animationDelay: `${index * 0.05}s` }}
                >
                  <div className="flex items-start justify-between gap-1.5">
                    <div className="flex-1 min-w-0">
                      <p className="font-bold text-rog-cyan mb-1 truncate text-xs leading-tight">{dep.name}</p>
                      <div className="space-y-0.5">
                        <p className="text-gray-200 text-xs leading-tight">
                          <span className="font-semibold">Current:</span>{' '}
                          <span className="font-mono bg-rog-gray px-1.5 py-0.5 rounded text-xs text-white">{dep.currentVersion}</span>
                        </p>
                        <p className="text-gray-200 text-xs leading-tight">
                          <span className="font-semibold">Latest:</span>{' '}
                          <span className="font-mono bg-rog-gray px-1.5 py-0.5 rounded text-xs text-rog-cyan">{dep.latestVersion}</span>
                        </p>
                      </div>
                    </div>
                    <div className="p-1 bg-rog-gray rounded rog-glow-cyan flex-shrink-0">
                      <AlertTriangle className="w-3.5 h-3.5 text-rog-cyan" />
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default ScanResults;
