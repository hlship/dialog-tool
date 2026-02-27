// Dialog Tool Frontend - Vanilla JavaScript

import './datastar.js';

window.dropdownSetup = function(element) {
if (element.open) {
  const listener = (e) => {
     if (!element.contains(e.target)) {
       element.open = false;
       document.removeEventListener('click', listener);
     }
   };
   setTimeout(() => document.addEventListener('click', listener), 0)}
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
