export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      colors: {
        brand: {
          50:  '#f0f4ff',
          100: '#e0e9ff',
          500: '#4361ee',
          600: '#3451d1',
          700: '#2a3fb5',
          900: '#1a2570',
        },
        surface: '#f8f9fc',
      }
    }
  },
  plugins: []
}
