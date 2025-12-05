  ;; See https://www.perplexity.ai/search/i-need-a-drop-down-menu-compon-ZpomMrE7TVuiNkzg0i.fpA
  
  (function () {
    const roots = document.querySelectorAll('[data-dropdown-root]');
    if (!roots.length) return;

    roots.forEach(function (root) {
      const button  = root.querySelector('[data-dropdown-button]');
      const menu    = root.querySelector('[data-dropdown-menu]');
      const label   = root.querySelector('[data-dropdown-label]');
      const options = root.querySelectorAll('[data-dropdown-option]');

      function openMenu() {
        menu.classList.remove('hidden');
        console.log("openMenu")
        button.setAttribute('aria-expanded', 'true');
      }

      function closeMenu() {
        menu.classList.add('hidden');
        button.setAttribute('aria-expanded', 'false');
      }

      function toggleMenu() {
        const isHidden = menu.classList.contains('hidden');
        if (isHidden) openMenu(); else closeMenu();
      }

      button.addEventListener('click', function (e) {
        e.stopPropagation();
        toggleMenu();
      });

      options.forEach(function (option) {
        option.addEventListener('click', function () {
          const value = option.getAttribute('data-dropdown-option');
          if (label) label.textContent = value;
          closeMenu(); // Datastar still sees the data-on:click on the option
        });
      });

      document.addEventListener('click', function (event) {
        if (!root.contains(event.target)) {
          closeMenu();
        }
      });

      document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
          closeMenu();
        }
      });
    });
  })();
