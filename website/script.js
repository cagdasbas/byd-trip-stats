/* BYD Trip Stats — marketing site interactions
   No dependencies, no build step. */

(function () {
  "use strict";

  /* ---- Mobile nav toggle ---- */
  const toggle = document.getElementById("navToggle");
  const links = document.getElementById("navLinks");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      const open = links.classList.toggle("is-open");
      toggle.setAttribute("aria-expanded", String(open));
    });
    // Close the menu after tapping a link
    links.querySelectorAll("a").forEach(function (a) {
      a.addEventListener("click", function () {
        links.classList.remove("is-open");
        toggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  /* ---- Add a solid background to the nav once scrolled ---- */
  const nav = document.getElementById("nav");
  if (nav) {
    const onScroll = function () {
      nav.classList.toggle("is-scrolled", window.scrollY > 12);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
  }

  /* ---- Screenshot placeholders ----
     Each <figure class="shot"> wraps an <img class="js-shot">. Until the real
     image exists at its src, show a labelled empty frame. As soon as the user
     drops a file at that path it loads and the placeholder disappears — no HTML
     edits needed. */
  document.querySelectorAll("img.js-shot").forEach(function (img) {
    const figure = img.closest(".shot");
    if (!figure) return;

    const markEmpty = function () { figure.classList.add("is-empty"); };
    const markFilled = function () { figure.classList.remove("is-empty"); };

    if (img.complete) {
      // naturalWidth === 0 means it failed / is a placeholder path
      if (img.naturalWidth === 0) markEmpty(); else markFilled();
    }
    img.addEventListener("error", markEmpty);
    img.addEventListener("load", function () {
      if (img.naturalWidth > 0) markFilled();
    });
  });

  /* ---- Scroll-reveal for sections ---- */
  const revealTargets = document.querySelectorAll(
    ".section__head, .card, .split__copy, .split__media, .shot, .privacy__points, .privacy__table, .chips li, .trust__grid, .cta__inner"
  );
  revealTargets.forEach(function (el) { el.classList.add("reveal"); });

  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-in");
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    revealTargets.forEach(function (el) { io.observe(el); });
  } else {
    revealTargets.forEach(function (el) { el.classList.add("is-in"); });
  }

  /* ---- Current year (footer, if needed later) ---- */
  const y = document.getElementById("year");
  if (y) y.textContent = String(new Date().getFullYear());
})();
