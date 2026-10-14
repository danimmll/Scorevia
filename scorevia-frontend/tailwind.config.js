/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        bg: 'var(--sv-bg)',
        surface: 'var(--sv-surface)',
        'surface-2': 'var(--sv-surface-2)',
        'surface-hover': 'var(--sv-surface-hover)',
        border: 'var(--sv-border)',
        'border-strong': 'var(--sv-border-strong)',
        'text-primary': 'var(--sv-text-primary)',
        'text-secondary': 'var(--sv-text-secondary)',
        'text-muted': 'var(--sv-text-muted)',
        accent: 'var(--sv-accent)',
        'accent-ink': 'var(--sv-accent-ink)',
        'accent-text': 'var(--sv-accent-text)',
        amber: 'var(--sv-amber)',
        'amber-ink': 'var(--sv-amber-ink)',
        'amber-text': 'var(--sv-amber-text)',
        flame: 'var(--sv-flame)',
        danger: 'var(--sv-danger)',
        warning: 'var(--sv-warning)',
      },
      fontFamily: {
        display: ['"Anton"', '"Archivo"', 'sans-serif'],
        sans: ['"Archivo"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
}