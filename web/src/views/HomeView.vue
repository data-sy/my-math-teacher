<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const go = (to) => router.push(to);

const conceptImageUrl = computed(() => 'images/concept-for-home.png');
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

        <div class="col-12">
            <router-link to="/concept">
                <div class="card">
                    <div class="flex justify-content-center text-900 font-medium text-4xl mb-2">선수 지식 알아보기</div>
                    <div class="flex justify-content-center">
                        <img :src="conceptImageUrl" alt="conceptImage" class="responsive-img" />
                    </div>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/diagnosis">
                <div class="card">
                    <div class="text-900 font-medium text-2xl">
                        &#9312; 진단 학습지 다운로드
                        <i v-tooltip="'중단원 별 시험지 제공\n - 개념 이해 여부 확인'" class="pi pi-question-circle text-xl mx-2"></i>
                    </div>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/record">
                <div class="card">
                    <div class="text-900 font-medium text-2xl">
                        &#9313; 채점하기
                        <i v-tooltip="'&#9312; 의 학습지 채점\n - 채점 결과를 AI에게 제공'" class="pi pi-question-circle text-xl mx-2"></i>
                    </div>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/result">
                <div class="card border-2 border-red-300">
                    <div class="flex align-items-center">
                        <span class="text-900 font-medium text-2xl">
                            &#9314; AI 분석 결과보기
                            <i v-tooltip="'&#9313; 의 채점 결과를 분석\n - 어떤 개념이 부족한지\n - 필요한 선수지식 확인 '" class="pi pi-question-circle text-xl mx-2"></i>
                        </span>
                        <span class="mx-3">(클릭해서 샘플을 확인하세요.)</span>
                    </div>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6 mb-7">
            <router-link to="/personal">
                <div class="card">
                    <div class="text-900 font-medium text-2xl">
                        &#9315; 맞춤 학습지 출제
                        <i v-tooltip="'&#9314; 에 따른 학습지 출제\n - 나에게 딱 맞는 문제 '" class="pi pi-question-circle text-xl mx-2"></i>
                    </div>
                </div>
            </router-link>
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
.responsive-img {
    max-width: 100%;
    height: auto;
}
</style>
