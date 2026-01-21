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
  if (document.getElementById('leakscanner-badge-container')) return;
  
  // Check saved state (collapsed/expanded and position)
  const savedState = localStorage.getItem('leakscanner-badge-collapsed');
  const isCollapsed = savedState === 'true';
  
  // Load saved position
  const savedPosition = localStorage.getItem('leakscanner-badge-position');
  let initialTop = 80;
  let initialLeft: number | null = null;
  let initialRight = 20;
  
  if (savedPosition) {
    try {
      const pos = JSON.parse(savedPosition);
      initialTop = pos.top || 80;
      if (pos.left !== undefined) {
        initialLeft = pos.left;
      } else {
        initialRight = pos.right || 20;
      }
    } catch (e) {
      // Use defaults if parsing fails
    }
  }
  
  // Create badge container
  const badgeContainer = document.createElement('div');
  badgeContainer.id = 'leakscanner-badge-container';
  const positionStyle = isCollapsed 
    ? `right: -140px; left: auto;`
    : initialLeft !== null 
      ? `left: ${initialLeft}px; right: auto;`
      : `right: ${initialRight}px; left: auto;`;
  
  badgeContainer.style.cssText = `
    position: fixed;
    top: ${initialTop}px;
    ${positionStyle}
    z-index: 10000;
    transition: ${isCollapsed ? 'right 0.4s cubic-bezier(0.4, 0, 0.2, 1)' : 'none'};
    cursor: move;
  `;
  
  // Create badge with elegant ROG Strix style (more subtle colors)
  const badge = document.createElement('div');
  badge.id = 'leakscanner-badge';
  badge.style.cssText = `
    background: linear-gradient(135deg, #0891b2 0%, #7c3aed 50%, #c026d3 100%);
    color: white;
    padding: 12px 16px;
    border-radius: 12px;
    cursor: move;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    backdrop-filter: blur(10px);
    border: 1px solid rgba(8, 145, 178, 0.25);
    animation: slideInRight 0.4s cubic-bezier(0.4, 0, 0.2, 1);
    user-select: none;
  `;
  
  badge.innerHTML = `
    <div style="
      width: 20px;
      height: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(255, 255, 255, 0.2);
      border-radius: 6px;
      padding: 3px;
    ">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
      </svg>
    </div>
    <span style="letter-spacing: 0.3px;">Scan Security</span>
    <button id="leakscanner-collapse-btn" style="
      background: rgba(255, 255, 255, 0.15);
      border: none;
      color: white;
      width: 20px;
      height: 20px;
      border-radius: 4px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
      margin-left: 4px;
      transition: all 0.2s;
    " title="Collapse">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M9 18l6-6-6-6"/>
      </svg>
    </button>
  `;
  
  // Create collapse tab - always visible and more noticeable
  const collapseTab = document.createElement('div');
  collapseTab.id = 'leakscanner-collapse-tab';
  collapseTab.style.cssText = `
    position: absolute;
    left: -35px;
    top: 50%;
    transform: translateY(-50%);
    width: 35px;
    height: 50px;
    background: linear-gradient(135deg, #0891b2 0%, #7c3aed 50%, #c026d3 100%);
    border-radius: 10px 0 0 10px;
    cursor: pointer;
    display: ${isCollapsed ? 'flex' : 'none'};
    align-items: center;
    justify-content: center;
    border: 1px solid rgba(8, 145, 178, 0.4);
    border-right: none;
    transition: all 0.3s;
    z-index: 10001;
  `;
  
  collapseTab.innerHTML = `
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M15 18l-6-6 6-6"/>
    </svg>
  `;
  
  collapseTab.addEventListener('mouseenter', () => {
    collapseTab.style.transform = 'translateY(-50%) scale(1.05)';
  });
  
  collapseTab.addEventListener('mouseleave', () => {
    collapseTab.style.transform = 'translateY(-50%) scale(1)';
  });
  
  // Collapse/Expand functionality
  const collapseBtn = badge.querySelector('#leakscanner-collapse-btn');
  let currentCollapsedState = isCollapsed;
  
  const toggleCollapse = () => {
    currentCollapsedState = !currentCollapsedState;
    localStorage.setItem('leakscanner-badge-collapsed', String(currentCollapsedState));
    
    const spanElement = badge.querySelector('span') as HTMLElement;
    const collapseBtnElement = badge.querySelector('#leakscanner-collapse-btn') as HTMLElement;
    
    if (currentCollapsedState) {
      // Hide button, but keep only shield icon visible
      const savedPos = localStorage.getItem('leakscanner-badge-position');
      if (savedPos) {
        try {
          const pos = JSON.parse(savedPos);
          badgeContainer.style.top = `${pos.top}px`;
        } catch (e) {}
      }
      badgeContainer.style.right = '-140px';
      badgeContainer.style.left = 'auto';
      badge.style.padding = '12px 12px';
      if (spanElement) spanElement.style.display = 'none';
      if (collapseBtnElement) collapseBtnElement.style.display = 'none';
      collapseTab.style.display = 'flex';
    } else {
      // Show full button - restore saved position or use default
      const savedPos = localStorage.getItem('leakscanner-badge-position');
      if (savedPos) {
        try {
          const pos = JSON.parse(savedPos);
          badgeContainer.style.top = `${pos.top}px`;
          if (pos.left !== undefined) {
            badgeContainer.style.left = `${pos.left}px`;
            badgeContainer.style.right = 'auto';
          } else {
            badgeContainer.style.right = `${pos.right || 20}px`;
            badgeContainer.style.left = 'auto';
          }
        } catch (e) {
          badgeContainer.style.right = '20px';
          badgeContainer.style.left = 'auto';
        }
      } else {
        badgeContainer.style.right = '20px';
        badgeContainer.style.left = 'auto';
      }
      badge.style.padding = '12px 16px';
      if (spanElement) spanElement.style.display = 'inline';
      if (collapseBtnElement) collapseBtnElement.style.display = 'flex';
      collapseTab.style.display = 'none';
    }
  };
  
  collapseBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleCollapse();
  });
  
  collapseTab.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleCollapse();
  });
  
  badge.addEventListener('mouseenter', () => {
    if (!currentCollapsedState) {
      badge.style.transform = 'translateY(-2px) scale(1.02)';
    }
  });
  
  badge.addEventListener('mouseleave', () => {
    badge.style.transform = 'translateY(0) scale(1)';
  });
  
  // Drag and drop functionality
  let isDragging = false;
  let dragStartX = 0;
  let dragStartY = 0;
  let initialX = 0;
  let initialY = 0;
  
  const startDrag = (e: MouseEvent) => {
    // Don't start drag if clicking on collapse button
    if ((e.target as HTMLElement).closest('#leakscanner-collapse-btn')) {
      return;
    }
    
    isDragging = true;
    badgeContainer.style.transition = 'none';
    badge.style.cursor = 'grabbing';
    
    const rect = badgeContainer.getBoundingClientRect();
    initialX = rect.left;
    initialY = rect.top;
    dragStartX = e.clientX;
    dragStartY = e.clientY;
    
    e.preventDefault();
  };
  
  const drag = (e: MouseEvent) => {
    if (!isDragging) return;
    
    const deltaX = e.clientX - dragStartX;
    const deltaY = e.clientY - dragStartY;
    
    let newLeft = initialX + deltaX;
    let newTop = initialY + deltaY;
    
    // Constrain to viewport
    const maxLeft = window.innerWidth - badgeContainer.offsetWidth;
    const maxTop = window.innerHeight - badgeContainer.offsetHeight;
    
    newLeft = Math.max(0, Math.min(newLeft, maxLeft));
    newTop = Math.max(0, Math.min(newTop, maxTop));
    
    badgeContainer.style.left = `${newLeft}px`;
    badgeContainer.style.top = `${newTop}px`;
    badgeContainer.style.right = 'auto';
  };
  
  const stopDrag = () => {
    if (!isDragging) return;
    
    isDragging = false;
    badgeContainer.style.transition = 'none';
    badge.style.cursor = 'move';
    
    // Save position
    const rect = badgeContainer.getBoundingClientRect();
    const savedLeft = rect.left;
    const savedTop = rect.top;
    
    localStorage.setItem('leakscanner-badge-position', JSON.stringify({
      top: savedTop,
      left: savedLeft
    }));
  };
  
  // Touch event handlers for trackpad/touchscreen
  const startDragTouch = (e: TouchEvent) => {
    if (e.touches.length !== 1) return;
    const touch = e.touches[0];
    
    // Don't start drag if clicking on collapse button
    if ((e.target as HTMLElement).closest('#leakscanner-collapse-btn')) {
      return;
    }
    
    isDragging = true;
    badgeContainer.style.transition = 'none';
    badge.style.cursor = 'grabbing';
    
    const rect = badgeContainer.getBoundingClientRect();
    initialX = rect.left;
    initialY = rect.top;
    dragStartX = touch.clientX;
    dragStartY = touch.clientY;
    
    e.preventDefault();
  };
  
  const dragTouch = (e: TouchEvent) => {
    if (!isDragging || e.touches.length !== 1) return;
    const touch = e.touches[0];
    
    const deltaX = touch.clientX - dragStartX;
    const deltaY = touch.clientY - dragStartY;
    
    let newLeft = initialX + deltaX;
    let newTop = initialY + deltaY;
    
    // Constrain to viewport
    const maxLeft = window.innerWidth - badgeContainer.offsetWidth;
    const maxTop = window.innerHeight - badgeContainer.offsetHeight;
    
    newLeft = Math.max(0, Math.min(newLeft, maxLeft));
    newTop = Math.max(0, Math.min(newTop, maxTop));
    
    badgeContainer.style.left = `${newLeft}px`;
    badgeContainer.style.top = `${newTop}px`;
    badgeContainer.style.right = 'auto';
    
    e.preventDefault();
  };
  
  const stopDragTouch = () => {
    if (!isDragging) return;
    
    isDragging = false;
    badgeContainer.style.transition = 'none';
    badge.style.cursor = 'move';
    
    // Save position
    const rect = badgeContainer.getBoundingClientRect();
    const savedLeft = rect.left;
    const savedTop = rect.top;
    
    localStorage.setItem('leakscanner-badge-position', JSON.stringify({
      top: savedTop,
      left: savedLeft
    }));
  };
  
  // Add drag handlers for mouse
  badge.addEventListener('mousedown', (e) => {
    // Check if it's a drag (mouse moved more than 5px) or click
    const startX = e.clientX;
    const startY = e.clientY;
    
    const handleMouseMove = (moveEvent: MouseEvent) => {
      const deltaX = Math.abs(moveEvent.clientX - startX);
      const deltaY = Math.abs(moveEvent.clientY - startY);
      
      if (deltaX > 5 || deltaY > 5) {
        // It's a drag
        startDrag(e);
        document.addEventListener('mousemove', drag);
        document.addEventListener('mouseup', () => {
          stopDrag();
          document.removeEventListener('mousemove', drag);
        }, { once: true });
      }
    };
    
    const handleMouseUp = () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };
    
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp, { once: true });
  });
  
  // Add touch handlers for trackpad/touchscreen
  badge.addEventListener('touchstart', (e) => {
    if (e.touches.length !== 1) return;
    const touch = e.touches[0];
    const startX = touch.clientX;
    const startY = touch.clientY;
    
    const handleTouchMove = (moveEvent: TouchEvent) => {
      if (moveEvent.touches.length !== 1) return;
      const moveTouch = moveEvent.touches[0];
      const deltaX = Math.abs(moveTouch.clientX - startX);
      const deltaY = Math.abs(moveTouch.clientY - startY);
      
      if (deltaX > 5 || deltaY > 5) {
        // It's a drag
        startDragTouch(e);
        document.addEventListener('touchmove', dragTouch, { passive: false });
        document.addEventListener('touchend', () => {
          stopDragTouch();
          document.removeEventListener('touchmove', dragTouch);
        }, { once: true });
      }
    };
    
    const handleTouchEnd = () => {
      document.removeEventListener('touchmove', handleTouchMove);
      document.removeEventListener('touchend', handleTouchEnd);
    };
    
    document.addEventListener('touchmove', handleTouchMove, { passive: false });
    document.addEventListener('touchend', handleTouchEnd, { once: true });
  }, { passive: false });
  
  badge.addEventListener('click', async (e) => {
    // Don't trigger click if it was a drag
    if (isDragging) {
      e.preventDefault();
      e.stopPropagation();
      return;
    }
    // Don't trigger if clicking collapse button
    if ((e.target as HTMLElement).closest('#leakscanner-collapse-btn')) {
      return;
    }
    
    // If collapsed, expand on click
    if (currentCollapsedState) {
      toggleCollapse();
      return;
    }
    
    try {
      // Store repository info
      if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
        await chrome.storage.local.set({ 
          currentRepository: { owner, name, platform } 
        });
      } else {
        chrome.runtime.sendMessage({
          type: 'SET_REPOSITORY',
          repository: { owner, name, platform }
        }).catch(() => {
          console.warn('LeakScanner: Could not store repository info');
        });
      }
      
      // Notify background to show badge on extension icon
      chrome.runtime.sendMessage({
        type: 'SHOW_BADGE',
        repository: { owner, name, platform }
      }).catch(() => {});
      
      // Visual feedback
      const originalHTML = badge.innerHTML;
      badge.style.background = 'linear-gradient(135deg, #10b981 0%, #059669 50%, #047857 100%)';
      badge.innerHTML = `
        <div style="
          width: 20px;
          height: 20px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: rgba(255, 255, 255, 0.2);
          border-radius: 6px;
          padding: 3px;
          animation: pulse 0.6s ease-out;
        ">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 6L9 17l-5-5"/>
          </svg>
        </div>
        <span style="letter-spacing: 0.3px;">Click extension icon ↑</span>
        <button id="leakscanner-collapse-btn" style="
          background: rgba(255, 255, 255, 0.15);
          border: none;
          color: white;
          width: 20px;
          height: 20px;
          border-radius: 4px;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 0;
          margin-left: 4px;
          transition: all 0.2s;
        " title="Collapse">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 18l6-6-6-6"/>
          </svg>
        </button>
      `;
      
      // Re-attach collapse button listener
      const newCollapseBtn = badge.querySelector('#leakscanner-collapse-btn');
      newCollapseBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleCollapse();
      });
      
      setTimeout(() => {
        badge.style.background = 'linear-gradient(135deg, #0891b2 0%, #7c3aed 50%, #c026d3 100%)';
        badge.innerHTML = originalHTML;
        
        // Re-attach collapse button listener again
        const restoredCollapseBtn = badge.querySelector('#leakscanner-collapse-btn');
        restoredCollapseBtn?.addEventListener('click', (e) => {
          e.stopPropagation();
          toggleCollapse();
        });
      }, 2000);
    } catch (error) {
      console.error('LeakScanner: Error storing repository info:', error);
    }
  });
  
  // Assemble and append
  badgeContainer.appendChild(badge);
  badgeContainer.appendChild(collapseTab);
  document.body.appendChild(badgeContainer);
  
  // Update isCollapsed reference
  Object.defineProperty(badgeContainer, 'isCollapsed', {
    get: () => localStorage.getItem('leakscanner-badge-collapsed') === 'true',
    set: (val) => localStorage.setItem('leakscanner-badge-collapsed', String(val))
  });
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
