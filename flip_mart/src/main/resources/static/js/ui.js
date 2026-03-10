// Small UI helpers (Bootstrap 5)
document.addEventListener("DOMContentLoaded", () => {
  // Auto-show any toasts rendered by the server
  const toastEls = document.querySelectorAll(".toast.app-toast");
  toastEls.forEach((el) => {
    try {
      const t = bootstrap.Toast.getOrCreateInstance(el, { delay: 3500 });
      t.show();
    } catch (e) {
      // ignore if bootstrap not loaded
    }
  });

  // Smooth scroll for anchors
  document.querySelectorAll('a[href^="#"]').forEach(a => {
    a.addEventListener("click", (e) => {
      const id = a.getAttribute("href");
      const target = document.querySelector(id);
      if (!target) return;
      e.preventDefault();
      target.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  });
});
