import React, { useState, useEffect } from 'react';
import { X, Save, Settings, ExternalLink, Key } from 'lucide-react';

interface SettingsPanelProps {
  onClose: () => void;
}

const SettingsPanel: React.FC<SettingsPanelProps> = ({ onClose }) => {
  const [apiUrl, setApiUrl] = useState('');
  const [githubToken, setGithubToken] = useState('');
  const [gitlabToken, setGitlabToken] = useState('');

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    const settings = await chrome.storage.sync.get([
      'apiUrl',
      'githubToken',
      'gitlabToken',
    ]);
    setApiUrl(settings.apiUrl || 'http://localhost:8080');
    setGithubToken(settings.githubToken || '');
    setGitlabToken(settings.gitlabToken || '');
  };

  const handleSave = async () => {
    await chrome.storage.sync.set({
      apiUrl,
      githubToken,
      gitlabToken,
    });
    onClose();
  };

  return (
    <div className="absolute inset-0 bg-rog-dark z-10 p-2.5 overflow-y-auto border border-rog-gray">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-1.5">
          <div className="p-1 bg-rog-gray rounded rog-glow-cyan">
            <Settings className="w-3.5 h-3.5 text-rog-cyan" />
          </div>
          <h2 className="text-base font-bold rog-text-gradient">Settings</h2>
        </div>
        <button
          onClick={onClose}
          className="p-1 hover:bg-rog-gray rounded transition-all duration-200 hover:scale-110 rog-glow-cyan"
        >
          <X className="w-3.5 h-3.5 text-rog-cyan" />
        </button>
      </div>

      <div className="space-y-2">
        <div className="bg-rog-darkGray border border-rog-purple/30 rounded-lg p-2 rog-glow-purple">
          <div className="flex items-start gap-1.5">
            <span className="text-xs">🚀</span>
            <div className="flex-1">
              <p className="text-xs font-semibold text-rog-purple mb-0.5">Quick Setup Guide</p>
              <p className="text-xs text-gray-400 leading-tight">
                Click "Create Token" buttons to open token creation pages. Copy the generated token and paste it here.
              </p>
            </div>
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-rog-cyan mb-1">
            Backend API URL
          </label>
          <input
            type="text"
            value={apiUrl}
            onChange={(e) => setApiUrl(e.target.value)}
            placeholder="http://localhost:8080"
            className="w-full px-2.5 py-1.5 text-xs border border-rog-cyan/30 rounded-lg focus:ring-2 focus:ring-rog-cyan focus:border-rog-cyan transition-all duration-200 bg-rog-darkGray text-white placeholder-gray-500 rog-glow-cyan"
          />
        </div>

        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="block text-xs font-semibold text-rog-cyan">
              GitHub Token <span className="text-gray-500 font-normal">(optional)</span>
            </label>
            <button
              onClick={() => {
                chrome.tabs.create({ url: 'https://github.com/settings/tokens/new?scopes=repo&description=LeakScanner' });
              }}
              className="flex items-center gap-1 text-xs text-rog-cyan hover:text-rog-cyanDark font-medium transition-colors"
            >
              <Key className="w-3 h-3" />
              <span>Create</span>
              <ExternalLink className="w-3 h-3" />
            </button>
          </div>
          <input
            type="password"
            value={githubToken}
            onChange={(e) => setGithubToken(e.target.value)}
            placeholder="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            className="w-full px-2.5 py-1.5 text-xs border border-rog-cyan/30 rounded-lg focus:ring-2 focus:ring-rog-cyan focus:border-rog-cyan transition-all duration-200 bg-rog-darkGray text-white placeholder-gray-500 rog-glow-cyan"
          />
          <p className="text-xs text-gray-400 mt-1 flex items-center gap-1">
            <span>💡</span>
            <span>Private repos • 5000/hour rate limit</span>
          </p>
        </div>

        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="block text-xs font-semibold text-rog-cyan">
              GitLab Token <span className="text-gray-500 font-normal">(optional)</span>
            </label>
            <button
              onClick={() => {
                chrome.tabs.create({ url: 'https://gitlab.com/-/user_settings/personal_access_tokens?name=LeakScanner&scopes=read_api,read_repository' });
              }}
              className="flex items-center gap-1 text-xs text-rog-cyan hover:text-rog-cyanDark font-medium transition-colors"
            >
              <Key className="w-3 h-3" />
              <span>Create</span>
              <ExternalLink className="w-3 h-3" />
            </button>
          </div>
          <input
            type="password"
            value={gitlabToken}
            onChange={(e) => setGitlabToken(e.target.value)}
            placeholder="glpat-xxxxxxxxxxxxxxxxxxxx"
            className="w-full px-2.5 py-1.5 text-xs border border-rog-cyan/30 rounded-lg focus:ring-2 focus:ring-rog-cyan focus:border-rog-cyan transition-all duration-200 bg-rog-darkGray text-white placeholder-gray-500 rog-glow-cyan"
          />
          <p className="text-xs text-gray-400 mt-1 flex items-center gap-1">
            <span>💡</span>
            <span>Private repos • 2000/hour rate limit</span>
          </p>
        </div>

        <button
          onClick={handleSave}
          className="w-full bg-gradient-to-r from-rog-cyan via-rog-purple to-rog-pink hover:from-rog-cyanDark hover:via-rog-purpleDark hover:to-rog-pinkDark text-white font-bold py-2 px-3 rounded-lg rog-glow hover:rog-glow transition-all duration-200 flex items-center justify-center gap-1.5 transform hover:scale-[1.02] active:scale-[0.98] text-xs uppercase tracking-wide"
        >
          <Save className="w-3.5 h-3.5" />
          <span>Save Settings</span>
        </button>
      </div>
    </div>
  );
};

export default SettingsPanel;
