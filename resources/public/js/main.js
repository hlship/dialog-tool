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

/**
 * Closes the browser window or navigates to about:blank if closing is not allowed.
 */
window.closeWindow = function() {
  window.close();
  // Fallback in case window.close() didn't work
  setTimeout(() => {
    window.location = 'about:blank';
  }, 100);
};

// Initialize on load
init();
