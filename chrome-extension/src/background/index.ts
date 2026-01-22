// Background service worker

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === 'SET_REPOSITORY') {
    // Store repository info from content script
    chrome.storage.local.set({ 
      currentRepository: message.repository 
    }).then(() => {
      sendResponse({ success: true });
    }).catch((error) => {
      console.error('Error storing repository:', error);
      sendResponse({ success: false, error: error.message });
    });
    return true; // Keep channel open for async response
  }
  
  if (message.type === 'SHOW_BADGE') {
    // Store repository info and show badge on extension icon
    chrome.storage.local.set({ 
      currentRepository: message.repository 
    }).then(() => {
      // Show badge to attract user attention
      chrome.action.setBadgeText({ text: '!' });
      chrome.action.setBadgeBackgroundColor({ color: '#00f2fe' });
      sendResponse({ success: true });
    }).catch((error) => {
      console.error('Error storing repository:', error);
      sendResponse({ success: false, error: error.message });
    });
    return true;
  }
  
  if (message.action === 'openPopup') {
    // Open extension popup by creating a new tab or focusing existing one
    chrome.action.setBadgeText({ text: '!' });
    // Store repository info for popup to read
    if (message.repository) {
      chrome.storage.local.set({ 
        currentRepository: message.repository 
      });
    }
    // Try to open popup (may not work programmatically, user needs to click icon)
    chrome.action.openPopup().catch(() => {
      // Popup can't be opened programmatically, user must click icon
    });
  }
  return true;
});

// Clear badge when popup is opened
chrome.action.onClicked?.addListener(() => {
  chrome.action.setBadgeText({ text: '' });
});

// Listen for tab updates to detect repository pages
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab.url) {
    const url = new URL(tab.url);
    if (url.hostname === 'github.com' || url.hostname === 'gitlab.com') {
      // Inject content script if needed
      chrome.scripting.executeScript({
        target: { tabId },
        files: ['content.js'],
      }).catch(() => {
        // Script might already be injected
      });
    }
  }
});
