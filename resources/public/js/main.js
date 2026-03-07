// Dialog Tool Frontend - Vanilla JavaScript

import './datastar.js';
import { computePosition, flip, shift, autoUpdate } from './floating-ui-dom.js';

window.positionDropdown = function(menu, evt) {
  // Clean up any previous autoUpdate listener
  if (menu._floatingCleanup) {
    menu._floatingCleanup();
    menu._floatingCleanup = null;
  }

  if (evt.newState !== 'open') {
    // Remove positioned so next open starts hidden (prevents flash)
    menu.classList.remove('positioned');
    return;
  }

  const btn = menu.previousElementSibling;

  // autoUpdate calls our callback immediately and again on scroll/resize,
  // keeping the menu anchored to the button.
  menu._floatingCleanup = autoUpdate(btn, menu, () => {
    computePosition(btn, menu, {
      placement: 'left-start',
      strategy: 'fixed',
      middleware: [flip(), shift({padding: 5})]
    }).then(({x, y}) => {
      Object.assign(menu.style, {
        position: 'fixed',
        margin: '0',
        left: x + 'px',
        top: y + 'px'
      });
      menu.classList.add('positioned');
    });
  });
}

/**
 * Shows a network error modal when the server is unreachable.
 * Built entirely in JS since the server may be down.
 * Uses the same structure and classes as server-rendered modals from modal.clj.
 */
let networkErrorShown = false;

function showNetworkErrorModal() {
  if (networkErrorShown) return;
  networkErrorShown = true;

  const container = document.getElementById('modal-container');
  if (!container) return;

  container.className = 'fixed inset-0 z-50 flex items-center justify-center bg-black/60';
  container.innerHTML = `
    <div class="bg-white rounded-lg shadow-xl max-w-full min-w-md mx-4"
         onclick="event.stopPropagation()">
      <div class="px-6 py-4 border-b border-gray-200">
        <h3 class="text-lg font-medium text-gray-900">Network Error</h3>
      </div>
      <div class="px-6 py-4">
        <p class="mb-4">Unable to reach the server. It may have crashed or been stopped.</p>
        <div class="flex justify-end gap-2">
          <button class="btn btn-primary" onclick="location.reload()">Reload</button>
        </div>
      </div>
    </div>
  `;
}

document.addEventListener('datastar-fetch', (e) => {
  const type = e.detail?.type;
  if (type === 'retrying' || type === 'retries-failed' || type === 'error') {
    showNetworkErrorModal();
  }
});

console.log('Dialog Tool UI initialized');
