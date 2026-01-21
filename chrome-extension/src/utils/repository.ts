import { Repository } from '../types';

export const getCurrentRepository = async (): Promise<Repository | null> => {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab.url) return null;

    const url = new URL(tab.url);
    
    // GitHub: https://github.com/owner/repo
    if (url.hostname === 'github.com') {
      const parts = url.pathname.split('/').filter(Boolean);
      if (parts.length >= 2) {
        return {
          owner: parts[0],
          name: parts[1],
          platform: 'github',
        };
      }
    }
    
    // GitLab: https://gitlab.com/owner/repo
    if (url.hostname === 'gitlab.com') {
      const parts = url.pathname.split('/').filter(Boolean);
      if (parts.length >= 2) {
        return {
          owner: parts[0],
          name: parts[1],
          platform: 'gitlab',
        };
      }
    }
    
    return null;
  } catch (error) {
    console.error('Error getting current repository:', error);
    return null;
  }
};
