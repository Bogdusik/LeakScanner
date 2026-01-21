// Script for quick GitHub token setup
// Run this code in the extension console (chrome://extensions/ -> LeakScanner -> Service Worker -> Console)
// Replace 'YOUR_GITHUB_TOKEN_HERE' with your actual token

(async () => {
  try {
    await chrome.storage.sync.set({
      githubToken: 'YOUR_GITHUB_TOKEN_HERE'
    });
    console.log('✅ GitHub token successfully added!');
    console.log('Now reload the extension or open the popup');
  } catch (error) {
    console.error('❌ Error adding token:', error);
  }
})();
