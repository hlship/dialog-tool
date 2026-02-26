// Dialog Tool Frontend - Vanilla JavaScript
// Replaces the previous ClojureScript implementation

import { attribute } from './datastar.js';

/**
 * Returns the computed margin-bottom of an element in pixels.
 */
function getMarginBottom(el) {
  return parseFloat(window.getComputedStyle(el).marginBottom);
}

/**
 * Registers the data-scroll-into-view attribute with Datastar.
 * When set to true, the element will be scrolled into view using smooth scrolling
 * with block: 'end' to ensure the entire element is visible, plus additional
 * scrolling based on the element's margin-bottom for visual breathing room.
 * Also focuses the first input element within the scrolled element.
 */
function registerScrollIntoViewAttribute() {
  attribute({
    name: 'scroll-into-view',
    requirement: { key: 'denied', value: 'must' },
    returnsValue: true,
    apply: (ctx) => {
      const el = ctx.el;
      const rx = ctx.rx;

      const checkAndScroll = () => {
        if (rx()) {
          el.scrollIntoView({ behavior: 'smooth', block: 'end' });

          // Add extra scroll based on element's margin-bottom
          const marginBottom = getMarginBottom(el);
          if (marginBottom > 0) {
            setTimeout(() => {
              window.scrollBy({ top: marginBottom, behavior: 'smooth' });
            }, 300);
          }

          // Focus the first input element
          const input = el.querySelector('input');
          if (input) {
            input.focus();
          }
        }
      };

      checkAndScroll();

      // Return cleanup function
      return () => {};
    }
  });
}

/**
 * Called once when the app loads.
 */
function init() {
  registerScrollIntoViewAttribute();
  console.log('Dialog Tool UI initialized');
}

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

// Initialize on load
init();
