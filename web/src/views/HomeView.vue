<script setup>
// import { ref } from 'vue';
// import { useApi } from '@/composables/api.js';

// const api = useApi();

// const getTestValue = ref(null);
// const postTestValue = ref(null);
// const getTest = async () => {
//     try {
//         const endpoint = '/api/v1/hello';
//         const response = await api.get(endpoint);
//         getTestValue.value = response;
//     } catch (err) {
//         console.error('getTest 에러 발생:', err);
//     }
// };
// const postTest = async () => {
//     try {
//         const endpoint = '/api/v1/hello';
//         const response = await api.post(endpoint);
//         postTestValue.value = response;
//     } catch (err) {
//         console.error('postTest 에러 발생:', err);
//     }
// };
</script>

<template>
    <div class="grid p-fluid">
        <!-- <button @click="getTest"> get 요청 버튼 </button>
        <div> {{ getTestValue }}</div>
        <button @click="postTest"> post 요청 버튼 </button>
        <div> {{ postTestValue }}</div> -->
        <div class="col-12">
            <router-link to="/conceptlist">
                <div class="card">
                    <div class="flex justify-content-center text-900 font-medium text-4xl mb-2">선수 지식 알아보기</div>
                    <div class="flex justify-content-center text-red-500 text-lg font-bold">(회원가입 없이 사용 가능)</div>
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
                <div class="card">
                    <div class="text-900 font-medium text-2xl">
                        &#9314; AI분석 결과보기
                        <i v-tooltip="'&#9313; 의 채점 결과를 분석\n - 어떤 개념이 부족한지\n - 필요한 선수지식 확인 '" class="pi pi-question-circle text-xl mx-2"></i>
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
