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

  /**
   * Keyboard navigation for search result buttons.
   * ArrowDown/Up move focus between result buttons (or back to the search input
   * when Up is pressed on the first result). Escape returns focus to the input.
   * Called from data-on:keydown on each result button.
   */
  navigateSearchResults(evt, el) {
    if (evt.key === 'ArrowDown') {
      evt.preventDefault();
      el.closest('li').nextElementSibling?.querySelector('button')?.focus();
    } else if (evt.key === 'ArrowUp') {
      evt.preventDefault();
      const prev = el.closest('li').previousElementSibling;
      if (prev) {
        prev.querySelector('button').focus();
      } else {
        document.getElementById('search-input').focus();
      }
    } else if (evt.key === 'Escape') {
      document.getElementById('search-input').focus();
    }
  },

  // ---------------------------------------------------------------------------
  // Nav graph: SVG arrows between knot nodes

  _arrowSvg: null,

  /**
   * Called once via data-init on #tree-pane.
   * Draws arrows immediately, then watches for DOM changes (SSE patches that
   * rebuild the tree) and redraws.  Disconnects before redrawing to avoid
   * recursive MutationObserver triggers.
   */
  initTreeGraph() {
    if (this._treeGraphReady) return;
    this._treeGraphReady = true;

    const pane = document.getElementById('tree-pane');
    if (!pane) return;
    // Defer initial draw until layout is complete so getBoundingClientRect is accurate.
    // After drawing, scroll root into horizontal center so the tree is visible.
    requestAnimationFrame(() => this.drawTreeArrows());
    const self = this;
    const observer = new MutationObserver(() => {
      observer.disconnect();
      // Another rAF here ensures the browser has finished reflowing after the
      // SSE patch before we measure node positions for the arrows.
      requestAnimationFrame(() => {
        self.drawTreeArrows();
        observer.observe(pane, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'data-active-knot'] });
      });
    });
    observer.observe(pane, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'data-active-knot'] });

    // Redraw arrows when the pane's outer size changes — catches both window
    // resize and the pane splitter being dragged (neither triggers a DOM mutation).
    new ResizeObserver(() => requestAnimationFrame(() => self.drawTreeArrows()))
      .observe(pane);
  },

  /**
   * Draws SVG bezier arrows from each node's pill bottom-centre to its
   * children's pill top-centres.  Replaces any previous SVG overlay.
   * After drawing, scrolls the active knot into view.
   */
  drawTreeArrows() {
    const pane = document.getElementById('tree-pane');
    if (!pane) return;

    this._arrowSvg?.remove();
    this._arrowSvg = null;

    // Build SVG overlay — absolutely positioned inside #tree-pane (.relative).
    // Size it to the full scrollable content so it scrolls with the nodes.
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    Object.assign(svg.style, {
      position: 'absolute', top: '0', left: '0',
      width: pane.scrollWidth + 'px',
      height: pane.scrollHeight + 'px',
      overflow: 'visible', pointerEvents: 'none',
    });

    // Arrowhead marker
    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
    marker.setAttribute('id', 'tree-arrow');
    marker.setAttribute('markerWidth', '6');
    marker.setAttribute('markerHeight', '6');
    marker.setAttribute('refX', '5');
    marker.setAttribute('refY', '3');
    marker.setAttribute('orient', 'auto');
    const tip = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    tip.setAttribute('d', 'M0,0 L0,6 L6,3 z');
    tip.setAttribute('fill', 'currentColor');
    marker.appendChild(tip);
    defs.appendChild(marker);
    svg.appendChild(defs);

    // Collect pill positions relative to the pane's scrollable content origin.
    // getBoundingClientRect gives viewport-relative coords; adjusting by scroll
    // offsets converts to content-relative coords that match the SVG coordinate space.
    const paneRect  = pane.getBoundingClientRect();
    const scrollLeft = pane.scrollLeft;
    const scrollTop  = pane.scrollTop;
    const nodes = {};
    pane.querySelectorAll('[data-tree-node-id]').forEach(el => {
      const id  = el.getAttribute('data-tree-node-id');
      const pid = el.getAttribute('data-parent-id');   // absent on root
      const r   = el.getBoundingClientRect();
      nodes[id] = {
        parentId: pid || null,
        cx:     r.left - paneRect.left + scrollLeft + r.width / 2,
        top:    r.top  - paneRect.top  + scrollTop,
        bottom: r.bottom - paneRect.top + scrollTop,
      };
    });

    // Draw a cubic bezier from parent bottom-centre to child top-centre.
    // Control points: depart vertically from parent (cp1 shares x1), arrive from
    // the parent's direction (cp2 shares x1 so the tangent at the child is angled).
    // orient="auto" on the marker then rotates the arrowhead to match that tangent.
    for (const n of Object.values(nodes)) {
      if (!n.parentId || !nodes[n.parentId]) continue;
      const p    = nodes[n.parentId];
      const x1   = p.cx,  y1 = p.bottom;
      const x2   = n.cx,  y2 = n.top;
      const gap  = Math.min(Math.abs(y2 - y1) / 3, 36);
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      // cp1: depart straight down from parent; cp2: arrive from parent's x-position
      // so the end tangent is (x2-x1, gap), matching the parent→child direction.
      path.setAttribute('d', `M${x1},${y1} C${x1},${y1+gap} ${x1},${y2-gap} ${x2},${y2}`);
      path.setAttribute('stroke', 'currentColor');
      path.setAttribute('stroke-opacity', '0.35');
      path.setAttribute('stroke-width', '1.5');
      path.setAttribute('fill', 'none');
      path.setAttribute('marker-end', 'url(#tree-arrow)');
      svg.appendChild(path);
    }

    this._arrowSvg = svg;
    pane.appendChild(svg);

    // Smooth-scroll the active knot into view in the pane's scroll container.
    const activeId = pane.getAttribute('data-active-knot');
    if (activeId) {
      pane.querySelector(`[data-tree-node-id="${activeId}"]`)
          ?.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' });
    }
  },

  // ---------------------------------------------------------------------------

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

  /**
   * Initialises drag-to-resize on the tree pane.
   * Called once from the tree pane's data-init attribute.
   *
   * Width is stored in a JS closure variable (currentWidth) so the
   * MutationObserver can restore it instantly if Datastar's morph resets
   * the inline style. It is also persisted to localStorage so it survives
   * page refreshes.
   */
  initTreePaneResize() {
    // Guard: data-init re-fires on every SSE update because h/action generates
    // a fresh expression each render. Without this, multiple MutationObservers
    // are created with different currentWidth closures and fight each other,
    // causing an infinite mutation loop that locks up the page.
    if (this._treePaneResizeReady) return;
    this._treePaneResizeReady = true;

    const handle = document.getElementById('tree-pane-handle');
    const pane   = document.getElementById('tree-pane-outer');
    if (!handle || !pane) return;

    // Restore from last session (page refresh) or keep server default.
    let currentWidth = localStorage.getItem('treePaneWidth') || null;
    if (currentWidth) pane.style.width = currentWidth;

    // If Datastar morphs #tree-pane-outer and resets the inline style back
    // to the server value, immediately restore the user's last-set width.
    new MutationObserver(() => {
      if (currentWidth && pane.style.width !== currentWidth) {
        pane.style.width = currentWidth;
      }
    }).observe(pane, { attributes: true, attributeFilter: ['style'] });

    handle.addEventListener('mousedown', (startEvt) => {
      startEvt.preventDefault();
      const startX     = startEvt.clientX;
      const startWidth = pane.getBoundingClientRect().width;

      const onMove = (e) => {
        const delta = e.clientX - startX;          // drag right → pane grows
        // Update currentWidth before setting style so the MutationObserver
        // sees the new value and does not immediately undo it.
        const maxWidth = Math.floor(window.innerWidth * 0.8);
        currentWidth = Math.min(maxWidth, Math.max(160, startWidth + delta)) + 'px';
        pane.style.width = currentWidth;
      };

      const onUp = () => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup',   onUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        if (currentWidth) localStorage.setItem('treePaneWidth', currentWidth);
      };

      document.body.style.cursor     = 'col-resize';
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup',   onUp);
    });
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

  // #modal-container only exists in the DOM when a server-rendered modal is open.
  // We can't rely on it being present, so create our own overlay from scratch.
  const overlay = document.createElement('div');
  overlay.id = 'network-error-modal';
  overlay.className = 'fixed inset-0 z-50 flex items-center justify-center bg-black/60';
  overlay.innerHTML = `
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
  document.body.appendChild(overlay);
}

// Wrap window.fetch to detect server connection failures immediately.
// Datastar's datastar-fetch events only cover SSE streams (and only after many
// retries), so action POSTs that fail with a network error go undetected.
// This wrapper catches any TypeError (network failure) on same-origin requests
// and shows the modal right away.
const _originalFetch = window.fetch;
window.fetch = async function(...args) {
  try {
    return await _originalFetch.apply(this, args);
  } catch (e) {
    if (e instanceof TypeError) {
      // TypeError means the request could not be made at all (server unreachable).
      showNetworkErrorModal();
    }
    throw e;
  }
};

/**
 * Datastar plugin: data-accel
 *
 * Declares a keyboard accelerator that triggers a click on the element.
 * Two modifier modes:
 *
 *   Cmd/Ctrl mode (default): requires Cmd (Mac) or Ctrl (Win/Linux).
 *     Use for global operations like Save, Undo, Search.
 *
 *   Alt mode (__alt): requires Alt/Option key, no Cmd/Ctrl.
 *     NOT suppressed by text-input focus, so these work from anywhere.
 *     Use for toolbar actions that must be reachable while typing.
 *
 * Both modes are suppressed when a modal is open or the element is disabled.
 *
 * Usage in Hiccup:
 *   :data-accel "s"                ;; Cmd/Ctrl+S
 *   :data-accel__shift "z"         ;; Cmd/Ctrl+Shift+Z
 *   :data-accel__alt "r"           ;; Alt+R  (works from text fields)
 *   :data-accel__alt__shift "r"    ;; Alt+Shift+R
 */
const isMac = navigator.platform.startsWith('Mac') || navigator.userAgent.includes('Mac');
const modSymbol = isMac ? '⌘' : 'Ctrl+';
const altSymbol = isMac ? '⌥' : 'Alt+';

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
    const isAlt = mods.has('alt');

    // Write the full label + shortcut into data-tip, which DaisyUI reads for its
    // ::before tooltip. The server emits data-preserve-attr="data-tip" so that
    // Datastar's morph never removes or overwrites data-tip between patches.
    const keyLabel = keyDisplayMap[accelKey] ?? accelKey.toUpperCase();
    const shiftPart = needsShift ? (isMac ? '⇧' : 'Shift+') : '';
    const shortcut = (isAlt ? altSymbol : modSymbol) + shiftPart + keyLabel;

    const buildTip = () => {
      const label = el.getAttribute('data-label') ?? '';
      el.setAttribute('data-tip', label ? `${label} (${shortcut})` : shortcut);
    };
    buildTip();

    // Re-build when data-label changes (e.g. "Trace Startup…" → "Trace…").
    const observer = new MutationObserver(buildTip);
    observer.observe(el, { attributes: true, attributeFilter: ['data-label'] });

    // On Mac, Option+letter produces a special character (e.g. Option+R → '®'),
    // so e.key is unreliable in Alt mode for letter keys. Use e.code instead
    // ('KeyR', 'KeyA', …). Arrow keys and other special keys are identical in
    // both e.key and e.code, so no special-casing is needed for them.
    const isSingleLetter = /^[a-z]$/i.test(accelKey);
    const expectedCode = (isAlt && isSingleLetter) ? 'Key' + accelKey.toUpperCase() : null;

    const handler = (e) => {
      if (isAlt) {
        // Alt mode: require Alt, no Cmd/Ctrl. Works even when a text field has focus.
        if (!e.altKey || e.metaKey || e.ctrlKey) return;
      } else {
        // Cmd/Ctrl mode: require Cmd or Ctrl.
        if (!(e.metaKey || e.ctrlKey)) return;
      }
      const keyMatch = expectedCode ? (e.code === expectedCode) : (e.key === accelKey);
      if (!keyMatch) return;
      if (!!e.shiftKey !== needsShift) return;
      if (el.hasAttribute('disabled')) return;
      if (document.querySelector('#modal-container > *')) return;
      e.preventDefault();
      el.click();
    };

    window.addEventListener('keydown', handler, true);
    return () => {
      window.removeEventListener('keydown', handler, true);
      observer.disconnect();
    };
  }
});

console.log('Dialog Tool UI initialized');
