<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const go = (to) => router.push(to);

const conceptImageUrl = computed(() => 'images/concept-for-home.png');

const steps = [
    { no: '①', label: '진단 학습지 받기', desc: '중단원별 시험지로 개념 이해 여부를 확인합니다.', icon: 'pi-file', to: '/diagnosis' },
    { no: '②', label: '채점하기', desc: '채점 결과를 AI에게 전달합니다.', icon: 'pi-check-square', to: '/record' },
    { no: '③', label: 'AI 약점 분석', desc: '어떤 개념이 부족한지·필요한 선수지식을 확인합니다.', icon: 'pi-chart-bar', to: '/result' },
    { no: '④', label: '맞춤 학습지 출제', desc: '분석에 따라 나에게 딱 맞는 문제를 출제합니다.', icon: 'pi-book', to: '/personal' }
];
</script>

<template>
    <div class="grid">
        <!-- 히어로: 가치 제안 + 1차 CTA -->
        <div class="col-12">
            <div class="card hero text-center py-6 px-4">
                <h1 class="text-4xl md:text-5xl font-bold text-900 line-height-2 mb-3">수학은 계단입니다.<br />어디서 막혔는지 AI가 짚어드립니다.</h1>
                <p class="text-lg md:text-xl text-700 mb-5 mx-auto hero-sub">이전 개념의 이해가 다음 학습을 좌우합니다. 진단 한 번으로 약한 고리를 찾아, 바로 채울 맞춤 학습지를 받아보세요.</p>
                <div class="flex flex-column sm:flex-row gap-3 justify-content-center">
                    <Button label="무료로 진단 시작" icon="pi pi-pencil" class="p-button-lg" @click="go('/diagnosis')" />
                    <Button label="개념 그래프 둘러보기 (로그인 불필요)" icon="pi pi-share-alt" class="p-button-lg p-button-outlined" @click="go('/concept')" />
                </div>
            </div>
        </div>

        <!-- 작동 방식: 진단 → 채점 → 분석 → 맞춤 학습지 -->
        <div class="col-12">
            <div class="card">
                <div class="text-900 font-bold text-2xl mb-4 text-center">이렇게 작동합니다</div>
                <div class="grid">
                    <div v-for="step in steps" :key="step.label" class="col-12 md:col-3">
                        <router-link :to="step.to" class="step-link">
                            <div class="step-box h-full flex flex-column align-items-center text-center p-3">
                                <span class="step-no text-primary font-bold text-2xl mb-2">{{ step.no }}</span>
                                <i :class="['pi', step.icon, 'text-3xl', 'text-primary', 'mb-3']"></i>
                                <div class="font-medium text-900 text-lg mb-2">{{ step.label }}</div>
                                <div class="text-600 text-sm">{{ step.desc }}</div>
                            </div>
                        </router-link>
                    </div>
                </div>
            </div>
        </div>

        <!-- 그래프 미리보기 + 샘플 결과 -->
        <div class="col-12 mb-5">
            <div class="card">
                <div class="flex flex-column md:flex-row align-items-center gap-4">
                    <router-link to="/concept" class="preview-img-link flex-1">
                        <img :src="conceptImageUrl" alt="개념 그래프 미리보기" class="responsive-img" />
                    </router-link>
                    <div class="flex flex-column gap-3 text-center md:text-left">
                        <div class="text-900 font-bold text-2xl">선수 지식, 한눈에 보기</div>
                        <p class="text-700 m-0">개념 간 선·후 관계를 그래프로 탐색해 보세요. 로그인 없이 바로 둘러볼 수 있습니다.</p>
                        <div class="flex flex-column sm:flex-row gap-2 justify-content-center md:justify-content-start">
                            <Button label="개념 그래프 둘러보기" icon="pi pi-share-alt" class="p-button-outlined" @click="go('/concept')" />
                            <Button label="샘플 결과 보기" icon="pi pi-arrow-right" icon-pos="right" class="p-button-text" @click="go('/result')" />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.hero {
    background: linear-gradient(180deg, var(--primary-50, #f0f3ff) 0%, var(--surface-card) 100%);
}
.hero-sub {
    max-width: 640px;
}
.step-link {
    text-decoration: none;
    color: inherit;
}
.step-box {
    border-radius: 12px;
    border: 1px solid var(--surface-border);
    transition: box-shadow 0.15s ease, transform 0.15s ease;
}
.step-box:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    transform: translateY(-2px);
}
.responsive-img {
    max-width: 100%;
    height: auto;
}
</style>
