
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-toast]').forEach((toast) => {
    setTimeout(() => toast.remove(), 4200);
  });

  const mobileToggle = document.querySelector('[data-mobile-toggle]');
  if (mobileToggle) {
    mobileToggle.addEventListener('click', () => {
      document.querySelector('.nav-links')?.classList.toggle('open');
    });
  }

  const modalHost = document.getElementById('confirmModal');
  const confirmMessage = document.getElementById('confirmMessage');
  const confirmAction = document.getElementById('confirmAction');
  document.querySelectorAll('[data-confirm]').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.preventDefault();
      if (!modalHost) return;
      confirmMessage.textContent = el.dataset.confirm || 'Are you sure?';
      confirmAction.setAttribute('href', el.getAttribute('href'));
      modalHost.classList.add('open');
    });
  });
  document.querySelectorAll('[data-close-modal]').forEach((el) => {
    el.addEventListener('click', () => modalHost?.classList.remove('open'));
  });
  modalHost?.addEventListener('click', (e) => { if (e.target === modalHost) modalHost.classList.remove('open'); });

  document.querySelectorAll('[data-password-toggle]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = document.getElementById(btn.dataset.passwordToggle);
      if (!input) return;
      input.type = input.type === 'password' ? 'text' : 'password';
      btn.textContent = input.type === 'password' ? 'Show' : 'Hide';
    });
  });

  const imageInput = document.querySelector('[data-image-preview-input]');
  const imagePreview = document.querySelector('[data-image-preview]');
  imageInput?.addEventListener('change', (e) => {
    const [file] = e.target.files;
    if (file && imagePreview) {
      imagePreview.src = URL.createObjectURL(file);
    }
  });
});
