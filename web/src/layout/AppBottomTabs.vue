<script setup>
import { useRoute, useRouter } from 'vue-router';

/**
 * 모바일 하단 탭 바 (셸 Phase 3)
 * ------------------------------------------------------------
 * 모바일(<960px)에서 상단 Menubar 햄버거를 대체하는 1차 내비.
 * 데스크톱에선 숨김(상단 글로벌 내비가 1차) — 960px 이하에서만 노출하며
 * 이 브레이크포인트는 PrimeVue Menubar 햄버거 전환점(960px)과 일치시킨다.
 * 표시 4탭: 개념 탐색 / 진단 / 결과 / 맞춤. 홈은 상단 로고로만 진입.
 * 채점(record)은 흐름의 통과 단계라 하단 탭에서 제외 — 흐름 페이지 상단 학습 스텝
 * 인디케이터(진단→채점→분석→맞춤)와 진단·결과 화면 동선에서 도달한다.
 * 현재 라우트 path 기준 active 탭이 자동 하이라이트된다(브랜드 컬러).
 */
const route = useRoute();
const router = useRouter();

const TABS = [
    { label: '개념', icon: 'pi pi-sitemap', to: '/concept' },
    { label: '진단', icon: 'pi pi-file', to: '/diagnosis' },
    { label: '결과', icon: 'pi pi-chart-line', to: '/result' },
    { label: '맞춤', icon: 'pi pi-book', to: '/personal' }
];

const isActive = (to) => route.path === to;
const go = (to) => {
    if (route.path !== to) router.push(to);
};
</script>

<template>
    <nav class="mmt-bottom-tabs" aria-label="모바일 주요 내비게이션">
        <button v-for="tab in TABS" :key="tab.to" type="button" class="mmt-bottom-tabs__item" :class="{ 'is-active': isActive(tab.to) }" :aria-current="isActive(tab.to) ? 'page' : undefined" @click="go(tab.to)">
            <i :class="tab.icon" class="mmt-bottom-tabs__icon" aria-hidden="true" />
            <span class="mmt-bottom-tabs__label">{{ tab.label }}</span>
        </button>
    </nav>
</template>

<style scoped>
/* 데스크톱 기본 숨김 — 모바일(<960px)에서만 노출(Menubar 햄버거 전환점과 일치) */
.mmt-bottom-tabs {
    display: none;
}

@media screen and (max-width: 960px) {
    .mmt-bottom-tabs {
        position: fixed;
        bottom: 0;
        left: 0;
        right: 0;
        z-index: 998; /* 고정 상단 Menubar(997) 위, 그래프 컨트롤 등 콘텐츠 위 */
        display: flex;
        background: var(--mmt-surface);
        border-top: 1px solid var(--mmt-border);
        /* iOS 홈 인디케이터 안전영역 확보 */
        padding-bottom: env(safe-area-inset-bottom, 0);
    }

    .mmt-bottom-tabs__item {
        position: relative;
        /* 독립 stacking context — active 알약(::before z-index:-1)을 글자 뒤·탭바 배경 앞에 가둔다 */
        isolation: isolate;
        flex: 1 1 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.25rem;
        min-height: 4rem;
        padding: 0.5rem 0.25rem;
        background: none;
        border: none;
        cursor: pointer;
        /* 아이콘(라인)·글자를 브랜드 보라로 */
        color: var(--mmt-brand);
        transition: color 0.15s ease;
    }

    .mmt-bottom-tabs__icon {
        font-size: 1.25rem;
    }

    .mmt-bottom-tabs__label {
        /* t-caption(색까지 묶인 클래스) 대신 글자 크기만 토큰으로 — 색은 item 에서 상속 */
        font-size: var(--mmt-fs-caption);
        line-height: 1;
    }

    /* 선택 탭: 보라 알약 배경 + 흰 글자로 구분 */
    .mmt-bottom-tabs__item.is-active {
        color: var(--mmt-brand-text);
    }
    /* 알약은 글자 뒤(z-index:-1)에 깔아 글자가 가려지지 않게 한다 */
    .mmt-bottom-tabs__item.is-active::before {
        content: '';
        position: absolute;
        inset: 0.4rem 0.5rem;
        background: var(--mmt-brand);
        border-radius: 999px;
        z-index: -1;
    }
}
</style>
