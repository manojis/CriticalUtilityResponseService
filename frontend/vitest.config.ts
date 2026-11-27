import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.ts';

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      globals: true,
      environment: 'jsdom',
      environmentOptions: { jsdom: { url: 'http://erms.test/' } },
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      restoreMocks: true,
      testTimeout: 15000,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'text-summary', 'html', 'lcov', 'json-summary'],
        include: ['src/**/*.{ts,tsx}'],
        exclude: [
          'src/**/*.test.{ts,tsx}',
          'src/test/**',
          'src/main.tsx',
          'src/vite-env.d.ts',
          'src/types/**',
        ],
        thresholds: {
          // Gate per docs/TESTING.md is ≥ 70% lines; we hold the suite to a stricter bar.
          lines: 85,
          statements: 85,
          functions: 85,
          branches: 80,
        },
      },
    },
  }),
);
