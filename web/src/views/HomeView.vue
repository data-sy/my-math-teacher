<script setup>
import { onMounted, reactive, ref, watch } from 'vue';

// 다운로드 확인 창
const displayConfirmation = ref(false);
const openConfirmation = () => {
    displayConfirmation.value = true;
};
const closeConfirmation = () => {
    displayConfirmation.value = false;
};

</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between">
                    <div>
                        <div class="text-900 font-medium text-xl mb-3"> 여기는 수학 지식을 점검하고, 부족한 부분을 찾아주는 사이트야. 어떤 선수지식이 필요한지 바로 알려주지! </div>
                        <div class="text-900 font-medium text-xl mb-3"> MMT와 함께 더 재미있게 수학 공부해보자구~!  </div>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-600 font-medium mb-3"> 특정 개념의 선수지식이 궁금하다면? => 선수 지식 알아보기 </span>
                        <span class="block text-500 font-medium mb-3"> 나에게 부족한 부분이 무엇인지 궁금하다면? => &#9312;->&#9313;->&#9314; </span>
                        <span class="block text-500 font-medium mb-3"> 1->2->3 순서대로 따라가면 돼</span>
                        <div>
                            <i class="pi pi-question-circle"></i> 마우스 오버/아웃으로 설명 추가
                            <i class="pi pi-user-edit" style="font-size: 2rem"></i> 로그인 후에는 user아이콘이 user-edit아이콘이 되도록
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/conceptlist">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> 선수 지식 알아보기 </div>
                    <span class="block text-600 font-medium mb-2"> 원하는 개념의 선수지식이 어떻게 되는지 알려줄게! </span>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/diagnosis">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> &#9312; 실력 점검하기 </div>
                    <span class="block text-600 font-medium mb-2"> 중단원에 따른 진단학습지가 준비되어 있어. 어떤 개념을 알고, 어떤 개념을 모르는지 확인해보자! </span>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/record">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> &#9313; 정오답 입력하기 </div>
                    <span class="block text-600 font-medium mb-2"> 푼 학습지의 정오답을 입력하면, 너에게 어떤 지식이 필요한지 AI가 알려줄거야! </span>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6 mb-7">
            <router-link to="/result">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> &#9314; 맞춤 학습  </div>
                    <span class="block text-600 font-medium mb-2"> AI 분석 결과에 따라 너에게 필요한 문제만 쏙쏙 제공해줄게! </span>
                </div>
            </router-link>
        </div>
    </div>
</template>
