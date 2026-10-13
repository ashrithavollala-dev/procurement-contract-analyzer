/** @type {import('tailwindcss').Config} */
// Design tokens: every colour used by the UI is a named token here (no ad-hoc hex values in components).
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef6ff',
          100: '#d9eaff',
          500: '#2f6fed',
          600: '#1f57c9',
          700: '#1a46a1',
          900: '#14295a',
        },
        surface: {
          DEFAULT: '#ffffff',
          muted: '#f5f7fb',
          sunken: '#eceff5',
          border: '#dde3ee',
        },
        ink: {
          DEFAULT: '#1c2333',
          muted: '#5b6478',
          faint: '#8a93a6',
        },
        status: {
          ok: '#1f8a4c',
          okBg: '#e3f5ea',
          warn: '#b7791f',
          warnBg: '#fdf3dc',
          danger: '#c53030',
          dangerBg: '#fde8e8',
          info: '#2b6cb0',
          infoBg: '#e3eefb',
          neutral: '#4a5568',
          neutralBg: '#edf0f5',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Consolas', 'monospace'],
      },
      borderRadius: {
        card: '0.75rem',
      },
      boxShadow: {
        card: '0 1px 2px rgba(20, 41, 90, 0.06), 0 1px 3px rgba(20, 41, 90, 0.08)',
      },
    },
  },
  plugins: [],
};
