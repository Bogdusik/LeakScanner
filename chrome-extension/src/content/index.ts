// Content script for injecting security badge into GitHub/GitLab pages

(function() {
  'use strict';
  
const injectSecurityBadge = async () => {
  const url = window.location.href;
  const isGitHub = url.includes('github.com');
  const isGitLab = url.includes('gitlab.com');
  
  if (!isGitHub && !isGitLab) return;
  
  // Extract repository info
  const pathParts = window.location.pathname.split('/').filter(Boolean);
  if (pathParts.length < 2) return;
  
  const owner = pathParts[0];
  const name = pathParts[1];
  const platform = isGitHub ? 'github' : 'gitlab';
  
  // Check if badge already exists
  if (document.getElementById('leakscanner-badge')) return;
  
  // Create badge with modern design
  const badge = document.createElement('div');
  badge.id = 'leakscanner-badge';
  badge.style.cssText = `
    position: fixed;
    top: 80px;
    right: 20px;
    z-index: 10000;
    background: linear-gradient(135deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%);
    color: white;
    padding: 14px 20px;
    border-radius: 12px;
    box-shadow: 0 10px 25px rgba(37, 99, 235, 0.3), 0 4px 10px rgba(0, 0, 0, 0.1);
    cursor: pointer;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
    font-size: 14px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 10px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255, 255, 255, 0.2);
    animation: slideInRight 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  `;
  
  badge.innerHTML = `
    <div style="
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(255, 255, 255, 0.2);
      border-radius: 6px;
      padding: 4px;
    ">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
      </svg>
    </div>
    <span style="letter-spacing: 0.3px;">Scan Security</span>
  `;
  
  badge.addEventListener('mouseenter', () => {
    badge.style.transform = 'translateY(-2px) scale(1.02)';
    badge.style.boxShadow = '0 15px 35px rgba(37, 99, 235, 0.4), 0 6px 15px rgba(0, 0, 0, 0.15)';
    badge.style.background = 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 50%, #1e40af 100%)';
  });
  
  badge.addEventListener('mouseleave', () => {
    badge.style.transform = 'translateY(0) scale(1)';
    badge.style.boxShadow = '0 10px 25px rgba(37, 99, 235, 0.3), 0 4px 10px rgba(0, 0, 0, 0.1)';
    badge.style.background = 'linear-gradient(135deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%)';
  });
  
  badge.addEventListener('mousedown', () => {
    badge.style.transform = 'translateY(0) scale(0.98)';
  });
  
  badge.addEventListener('mouseup', () => {
    badge.style.transform = 'translateY(-2px) scale(1.02)';
  });
  
  badge.addEventListener('click', async () => {
    try {
      // Check if chrome.storage is available
      if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
        // Store repository info and notify user to click extension icon
        await chrome.storage.local.set({ 
          currentRepository: { owner, name, platform } 
        });
      } else {
        // Fallback: use message passing if storage is not available
        chrome.runtime.sendMessage({
          type: 'SET_REPOSITORY',
          repository: { owner, name, platform }
        }).catch(() => {
          console.warn('LeakScanner: Could not store repository info');
        });
      }
      
      // Animated notification with checkmark
      badge.style.background = 'linear-gradient(135deg, #10b981 0%, #059669 50%, #047857 100%)';
      badge.innerHTML = `
        <div style="
          width: 24px;
          height: 24px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: rgba(255, 255, 255, 0.2);
          border-radius: 6px;
          padding: 4px;
          animation: pulse 0.6s ease-out;
        ">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 6L9 17l-5-5"/>
          </svg>
        </div>
        <span style="letter-spacing: 0.3px;">Click extension icon ↑</span>
      `;
      
      setTimeout(() => {
        badge.style.background = 'linear-gradient(135deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%)';
        badge.innerHTML = `
          <div style="
            width: 24px;
            height: 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 6px;
            padding: 4px;
          ">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
          </div>
          <span style="letter-spacing: 0.3px;">Scan Security</span>
        `;
      }, 2500);
    } catch (error) {
      console.error('LeakScanner: Error storing repository info:', error);
      // Show error state
      badge.style.background = 'linear-gradient(135deg, #ef4444 0%, #dc2626 50%, #b91c1c 100%)';
      badge.innerHTML = `
        <div style="
          width: 24px;
          height: 24px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: rgba(255, 255, 255, 0.2);
          border-radius: 6px;
          padding: 4px;
        ">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </div>
        <span style="letter-spacing: 0.3px;">Click extension icon ↑</span>
      `;
      setTimeout(() => {
        badge.style.background = 'linear-gradient(135deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%)';
        badge.innerHTML = `
          <div style="
            width: 24px;
            height: 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 6px;
            padding: 4px;
          ">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
          </div>
          <span style="letter-spacing: 0.3px;">Scan Security</span>
        `;
      }, 2500);
    }
  });
  
  document.body.appendChild(badge);
};

// Wait for page to load
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', injectSecurityBadge);
} else {
  injectSecurityBadge();
}

// Re-inject on navigation (SPA)
let lastUrl = location.href;
new MutationObserver(() => {
  const url = location.href;
  if (url !== lastUrl) {
    lastUrl = url;
    setTimeout(injectSecurityBadge, 1000);
  }
}).observe(document, { subtree: true, childList: true });

})();
