// Dialog Tool Frontend - Vanilla JavaScript

import { attribute } from './datastar.js';
import { computePosition, flip, shift, autoUpdate } from './floating-ui-dom.js';

// All functions called from server-rendered Datastar attribute strings live here.
window.sk = {

  positionDropdown(menu, evt) {
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
  },

  // --- Flash messages ---
  // Managed entirely in JS so they survive Datastar DOM morphs.

  _flashEl: null,
  _flashTimer: null,

  _dismissFlash() {
    if (this._flashTimer) {
      clearTimeout(this._flashTimer);
      this._flashTimer = null;
    }
    if (this._flashEl) {
      this._flashEl.remove();
      this._flashEl = null;
    }
  },

  showFlash(message, type = 'info') {
    const isError = type === 'error';
    this._dismissFlash();

    const wrapper = document.createElement('div');
    wrapper.className = 'fixed top-20 left-1/2 -translate-x-1/2 z-50';
    wrapper.style.pointerEvents = isError ? 'auto' : 'none';

    const inner = document.createElement('div');
    inner.className = isError
      ? 'flex items-center gap-3 bg-red-600 text-white px-6 py-3 rounded-lg shadow-lg'
      : 'bg-blue-600 text-white px-6 py-3 rounded-lg shadow-lg transition-opacity duration-500';
    if (!isError) inner.style.opacity = '0';

    const msg = document.createElement('span');
    msg.textContent = message;
    inner.appendChild(msg);

    if (isError) {
      inner.setAttribute('tabindex', '-1');
      inner.addEventListener('keydown', (e) => { if (e.key === 'Escape') this._dismissFlash(); });
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'ml-2 opacity-80 hover:opacity-100 text-lg font-bold cursor-pointer';
      btn.textContent = '✕';
      btn.addEventListener('click', () => this._dismissFlash());
      inner.appendChild(btn);
    }

    wrapper.appendChild(inner);
    document.body.appendChild(wrapper);
    this._flashEl = wrapper;

    if (isError) {
      inner.focus();
    } else {
      requestAnimationFrame(() => {
        inner.style.opacity = '1';
        this._flashTimer = setTimeout(() => {
          inner.style.opacity = '0';
          this._flashTimer = setTimeout(() => this._dismissFlash(), 600);
        }, 2000);
      });
    }
  },

  // --- DOM helpers ---

  scrollKnotIntoView(id) {
    const el = document.getElementById('knot-' + id);
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const viewportHeight = window.innerHeight;
    if (rect.bottom > viewportHeight) {
      // Knot is below the viewport: scroll down and peek at what follows.
      window.scrollBy({ top: rect.bottom - viewportHeight + 80, behavior: 'smooth' });
    } else if (rect.top < 0) {
      // Knot is above the viewport: scroll up just enough to show it.
      el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
    // else: already fully visible — no scroll needed.
  },

  resetAndFocusCommandInput() {
    const el = document.getElementById('new-command-input');
    if (el) {
      el.value = '';
      el.dispatchEvent(new Event('input', {bubbles: true}));
      el.scrollIntoView({block: 'nearest', behavior: 'smooth'});
      el.focus({preventScroll: true});
    }
  },

  /**
   * Source preview popup on hover.
   *
   * A static #source-preview-popup element lives in index.html (initially hidden).
   * The delay before showing is handled by Datastar's __debounce modifier on the
   * data-on:mouseenter attribute. This JS just fetches the preview content,
   * injects it, and positions the popup via Floating UI.
   */
  _previewCleanup: null,
  _previewController: null,

  async showSourcePreview(el, nodePath) {
    this.hideSourcePreview();
    const popup = document.getElementById('source-preview-popup');
    if (!popup) return;
    try {
      this._previewController = new AbortController();
      const resp = await fetch('/action/source-preview/' + nodePath, {
        signal: this._previewController.signal
      });
      this._previewController = null;
      if (!resp.ok) return;
      popup.innerHTML = await resp.text();
      popup.classList.remove('hidden');
      this._previewCleanup = autoUpdate(el, popup, () => {
        computePosition(el, popup, {
          placement: 'top',
          strategy: 'fixed',
          middleware: [flip(), shift({ padding: 8 })]
        }).then(({ x, y }) => {
          Object.assign(popup.style, { left: x + 'px', top: y + 'px' });
        });
      });
    } catch (e) {
      // Aborted or network error — silently ignore
    }
  },

  hideSourcePreview() {
    if (this._previewController) {
      this._previewController.abort();
      this._previewController = null;
    }
    if (this._previewCleanup) {
      this._previewCleanup();
      this._previewCleanup = null;
    }
    const popup = document.getElementById('source-preview-popup');
    if (popup) {
      popup.classList.add('hidden');
      popup.innerHTML = '';
    }
  },

};

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

// Maps special key names to display symbols for tooltips
const keyDisplayMap = {
  'Delete': isMac ? '⌫' : 'Del',
  'ArrowUp': '↑',
  'ArrowDown': '↓',
  'ArrowLeft': '←',
  'ArrowRight': '→',
  'Home': isMac ? '↖' : 'Home',
  'End': isMac ? '↘' : 'End',
};

attribute({
  name: 'accel',
  requirement: { key: 'denied', value: 'must' },
  apply({ el, value, mods }) {
    const accelKey = value;
    const needsShift = mods.has('shift');

    // Set DaisyUI tooltip showing the shortcut, e.g. "⌘S", "⌘⌫", or "Ctrl+Shift+Z"
    // Appends the shortcut to any existing data-tip text (e.g. "Bless (⌘B)").
    const keyLabel = keyDisplayMap[accelKey] ?? accelKey.toUpperCase();
    const shortcut = modSymbol + (needsShift ? (isMac ? '⇧' : 'Shift+') : '') + keyLabel;
    const existing = el.getAttribute('data-tip');
    el.setAttribute('data-tip', existing ? `${existing} (${shortcut})` : shortcut);

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
