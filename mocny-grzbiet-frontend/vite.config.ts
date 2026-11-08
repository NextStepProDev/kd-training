import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    exclude: ['e2e/**', 'node_modules/**'],
  },
  server: {
    port: 5175,
    allowedHosts: ['.ngrok-free.app'],
    proxy: {
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/sitemap.xml': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/login/oauth2': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
  build: {
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    rollupOptions: {
      output: {
        // Vite 8 bundles with Rolldown, which only accepts the function form of
        // manualChunks. Order matters: @tanstack before the generic react/* match.
        manualChunks: (id) => {
          if (!id.includes('node_modules')) return;
          if (id.includes('@tanstack')) return 'query';
          if (
            id.includes('react-router') ||
            id.includes('react-dom') ||
            id.includes('/react/') ||
            id.includes('scheduler')
          ) {
            return 'vendor';
          }
        },
      },
    },
  },
});
