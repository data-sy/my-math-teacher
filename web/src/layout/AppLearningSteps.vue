<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

/**
 * 학습 스텝 인디케이터 (셸 Phase 2)
 * ------------------------------------------------------------
 * 학습 흐름 4단계(진단 → 채점 → 분석 → 맞춤)를 흐름 페이지에서만
 * "지금 몇 번째 단계인지" 맥락으로 표시한다. 표시 전용(PrimeVue Steps
 * readonly) — 단계 클릭으로 라우팅 이동하지 않는다. 현재 라우트 path 와
 * item.to 매칭으로 active 단계가 자동 하이라이트된다.
 * route → step 매핑은 이 컴포넌트가 단일 출처다(AppLayout 셸에 1회 배선).
 */
const route = useRoute();

const FLOW_STEPS = [
    { label: '진단', to: '/diagnosis' },
    { label: '채점', to: '/record' },
    { label: '분석', to: '/result' },
    { label: '맞춤', to: '/personal' }
];

// 흐름 4페이지에서만 노출 — 개념 탐색·홈·로그인 등 흐름 밖 페이지에선 숨김
const isFlowPage = computed(() => FLOW_STEPS.some((s) => s.to === route.path));
</script>

<template>
    <div v-if="isFlowPage" class="mmt-learning-steps">
        <div class="mmt-learning-steps__inner">
            <Steps :model="FLOW_STEPS" :readonly="true" aria-label="학습 단계" />
        </div>
    </div>
</template>

<style scoped>
.mmt-learning-steps {
    background: var(--mmt-surface);
    border-bottom: 1px solid var(--mmt-border);
}
.mmt-learning-steps__inner {
    max-width: 1400px;
    margin: 0 auto;
    padding: 1rem 2rem;
}
/* readonly Steps 는 클릭 불가이므로 손가락 커서·호버 강조를 끈다 */
.mmt-learning-steps :deep(.p-steps .p-steps-item .p-menuitem-link) {
    cursor: default;
}
</style>
