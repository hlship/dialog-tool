// Dialog Tool Frontend - Vanilla JavaScript

import { attribute } from './datastar.js';
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

/**
 * Datastar plugin: data-accel
 *
 * Declares a keyboard accelerator (Cmd on Mac, Ctrl on Windows/Linux) that
 * triggers a click on the element. The key character comes from the attribute
 * key, and the __shift modifier requires Shift to be held.
 *
 * Suppressed when a modal is open or the element has the disabled attribute.
 *
 * Usage in Hiccup:
 *   :data-accel "s"           ;; Cmd/Ctrl+S triggers el.click()
 *   :data-accel "z"           ;; Cmd/Ctrl+Z
 *   :data-accel__shift "z"    ;; Cmd/Ctrl+Shift+Z
 */
const isMac = navigator.platform.startsWith('Mac') || navigator.userAgent.includes('Mac');
const modSymbol = isMac ? '⌘' : 'Ctrl+';

attribute({
  name: 'accel',
  requirement: { key: 'denied', value: 'must' },
  apply({ el, value, mods }) {
    const accelKey = value;
    const needsShift = mods.has('shift');

    // Set DaisyUI tooltip showing the shortcut, e.g. "⌘S" or "Ctrl+Shift+Z"
    const label = modSymbol + (needsShift ? (isMac ? '⇧' : 'Shift+') : '') + accelKey.toUpperCase();
    el.setAttribute('data-tip', label);

    const handler = (e) => {
      if (!(e.metaKey || e.ctrlKey)) return;
      if (e.key !== accelKey) return;
      if (!!e.shiftKey !== needsShift) return;
      if (el.hasAttribute('disabled')) return;
      if (document.querySelector('#modal-container > *')) return;
      e.preventDefault();
      el.click();
    };

    window.addEventListener('keydown', handler, true);
    return () => window.removeEventListener('keydown', handler, true);
  }
});

console.log('Dialog Tool UI initialized');
