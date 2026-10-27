/* Label Trunc theme: small progressive enhancements. Everything works without it. */
(function () {
  'use strict';

  // Menu sheet (native dialog: Escape closes, focus returns to the button).
  var sheet = document.querySelector('[data-lt-menu]');
  var opener = document.querySelector('[data-lt-menu-open]');
  if (sheet && opener && typeof sheet.showModal === 'function') {
    opener.addEventListener('click', function () { sheet.showModal(); });
    sheet.querySelectorAll('[data-lt-menu-close]').forEach(function (b) {
      b.addEventListener('click', function () { sheet.close(); });
    });
    sheet.addEventListener('click', function (e) { if (e.target === sheet) sheet.close(); });
  }

  // Gallery: counter follows the photo in view on the swipeable phone row.
  var gallery = document.querySelector('[data-lt-gallery]');
  if (gallery && 'IntersectionObserver' in window) {
    var current = gallery.querySelector('[data-lt-gallery-current]');
    var items = gallery.querySelectorAll('.lt-product__image');
    if (current && items.length > 1) {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) {
          if (en.isIntersecting && en.intersectionRatio > 0.6) {
            current.textContent = String(Array.prototype.indexOf.call(items, en.target) + 1);
          }
        });
      }, { threshold: [0.6] });
      items.forEach(function (i) { io.observe(i); });
    }
  }

  // Phone buy bar: appears only after the main cart button has scrolled out of view.
  var bar = document.querySelector('[data-lt-buybar]');
  var buy = document.getElementById('lt-buy');
  if (bar && buy && 'IntersectionObserver' in window) {
    var seen = false;
    new IntersectionObserver(function (entries) {
      var e = entries[0];
      if (e.isIntersecting) { seen = true; bar.hidden = true; return; }
      bar.hidden = !(seen && e.boundingClientRect.top < 0);
    }).observe(buy);
  }

  // Shop toolbar: apply select changes at once; keep "Apply" for keyboard users.
  var form = document.querySelector('[data-lt-autosubmit]');
  if (form) {
    var sold = form.querySelector('[data-lt-sold]');
    var soldOff = form.querySelector('[data-lt-sold-off]');
    var sync = function () { if (sold && soldOff) soldOff.disabled = sold.checked; };
    sync();
    form.querySelectorAll('select, [data-lt-sold]').forEach(function (el) {
      el.addEventListener('change', function () { sync(); form.submit(); });
    });
  }

  // Notices and form status messages take focus so screen readers announce them.
  var status = document.querySelector('.lt-form-status, .woocommerce-error');
  if (status) { status.setAttribute('tabindex', '-1'); status.focus({ preventScroll: false }); }
})();
