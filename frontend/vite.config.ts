/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// During development (`npm run dev`), API calls go to the Spring Boot app on port 8080.
const api = 'http://localhost:8080';
const apiPaths = ['/accounts', '/deposits', '/transfers', '/transactions', '/instruments', '/ledger', '/v3', '/swagger-ui'];

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1200,
  },
  server: {
    port: 5173,
    proxy: Object.fromEntries(apiPaths.map((path) => [path, api])),
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
