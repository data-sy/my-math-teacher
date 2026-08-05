import { defineConfig } from '@playwright/test'

// 프로덕션 대상 설정 — 로컬 dev 서버를 띄우지 않고 라이브 사이트를 친다.
// 기본 playwright.config.ts(localhost:5173 + webServer)와 분리 유지.
export default defineConfig({
  testDir: './e2e',
  testMatch: /prod-.*\.spec\.ts/,
  timeout: 180_000,
  reporter: [['list']],
  use: {
    baseURL: 'https://www.my-math-teacher.com',
    viewport: { width: 390, height: 844 }, // 모바일 퍼스트 기획 기준
    hasTouch: true,
    screenshot: 'only-on-failure',
    video: 'off',
  },
})
