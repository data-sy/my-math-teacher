import { defineConfig } from '@playwright/test'

// 검증 규약: 모바일 390px 뷰포트 (기획물 기준) · mock 모드 dev 서버
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL: 'http://localhost:5173',
    viewport: { width: 390, height: 844 },
    hasTouch: true,
  },
  webServer: {
    command: 'npm run dev',
    port: 5173,
    reuseExistingServer: true,
  },
})
