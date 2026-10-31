/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#04050a',
          900: '#070910',
          800: '#0b0f1c',
          700: '#111728',
          600: '#18203a',
        },
        navy: {
          900: '#060b1d',
          800: '#0a1230',
          700: '#101a44',
          600: '#16245c',
        },
        accent: {
          DEFAULT: '#4c7dff',
          soft: '#7aa0ff',
          deep: '#2b4fd6',
        },
        violet: {
          glow: '#8b5cf6',
          deep: '#5b32c4',
        },
        gold: {
          DEFAULT: '#d8b35f',
          bright: '#f2d48a',
        },
        live: '#22d39a',
        danger: '#ff5f6d',
      },
      fontFamily: {
        display: ['Sora', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      boxShadow: {
        glass: '0 1px 0 0 rgba(255,255,255,0.06) inset, 0 24px 60px -24px rgba(0,0,0,0.9)',
        glow: '0 0 40px -8px rgba(76,125,255,0.55)',
        'glow-gold': '0 0 44px -10px rgba(216,179,95,0.6)',
      },
      backgroundImage: {
        'grid-faint':
          'linear-gradient(to right, rgba(255,255,255,0.035) 1px, transparent 1px), linear-gradient(to bottom, rgba(255,255,255,0.035) 1px, transparent 1px)',
        'radial-spot': 'radial-gradient(60% 60% at 50% 40%, var(--tw-gradient-stops))',
      },
      keyframes: {
        'pulse-ring': {
          '0%': { transform: 'scale(0.9)', opacity: '0.8' },
          '70%': { transform: 'scale(1.6)', opacity: '0' },
          '100%': { transform: 'scale(1.6)', opacity: '0' },
        },
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(14px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'slide-in': {
          '0%': { opacity: '0', transform: 'translateX(28px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        'price-pop': {
          '0%': { transform: 'scale(1)' },
          '35%': { transform: 'scale(1.09)', color: '#22d39a' },
          '100%': { transform: 'scale(1)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-7px)' },
        },
        'border-flow': {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
      },
      animation: {
        'pulse-ring': 'pulse-ring 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'fade-up': 'fade-up 0.6s cubic-bezier(0.22, 1, 0.36, 1) both',
        'slide-in': 'slide-in 0.4s cubic-bezier(0.22, 1, 0.36, 1) both',
        shimmer: 'shimmer 1.8s infinite',
        'price-pop': 'price-pop 0.6s ease-out',
        float: 'float 6s ease-in-out infinite',
        'border-flow': 'border-flow 6s ease infinite',
      },
    },
  },
  plugins: [],
};
