<script setup>
import { ref, watch, onMounted } from 'vue';
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';
import { useStore } from 'vuex';
import axios from 'axios';
import TitleService from '@/service/TitleService';

const store = useStore();
const api = useApi();
const { htmlToPdf } = useHtmlToPdf();

// 유저 정보
const isLoggedIn = ref(false);
const userDetail = ref({
    userName: '',
    userBirthdate: ''
});
const userGrade = ref('');
onMounted(async() => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null;
    watch(() => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    )
    if (isLoggedIn.value) {
        try {
            const endpoint = 'users';
            const response = await api.get(endpoint);
            userDetail.value = response;
            userGrade.value = TitleService.calculateGrade(userDetail.value.userBirthdate);

        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    } else {
        console.log("사용자가 로그인하지 않았습니다. 유저 정보를 건너뜁니다.");
    }
});
// 학습지 미리보기
const testDetail = ref([]);
const testId = ref(null);
const isImageExist = ref(false);
onMounted(async() => {
    testId.value = 491;
    if (testId.value >= 491 && testId.value <= 495) {
        isImageExist.value = true;
    } else {
        isImageExist.value = false;
    }
    // console.log(isImageExist.value);
    try {
        const endpoint = `/tests/detail/${testId.value}`;
        const response = await api.get(endpoint);
        testDetail.value = response;
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
});
// title에 넣을 데이터들
const schoolLevel = ref('고등');
const grade = ref('수학');
const semester = ref('상');
const testName = ref('복소수와 이차방정식(1)');
// 날짜
const formattedDate = ref('');
const updateDate = () => {
  formattedDate.value = TitleService.updateDate();
};
onMounted(() => {
  updateDate();
  setInterval(updateDate, 1000); // 1초마다 갱신
});
// 문항이미지 비율
const computeAspectRatio = (num) => {
    // 6보다 작거나 2의 배수가 아닐 때는 기본값 5/4
    // 6일 때는 40/35
    // 6보다 큰 2의 배수이면서 6의 배수 아닐 때는 1/1
    // 6보다 큰 2의 배수이면서 6의 배수일 때는 40/37
    if (num < 6 || num % 2 !== 0) {
        return { 'aspect-ratio': '5/4' };
    } else if (num % 6 !== 0) {
        return { 'aspect-ratio': '1/1' };
    } else if (num === 6) {
        return { 'aspect-ratio': '40/35' };
    } else {
        return { 'aspect-ratio': '40/37' };
    }
};
// 답안 원문자 표현 (HTML 엔티티)
const renderItemAnswer = (text) => {
    return text;
};
// pdf 다운로드
const pdfAreaRef = ref(null);
const generatePdf = () => {
    const fileName = `MMT_${testName.value}`;
  htmlToPdf(pdfAreaRef.value, fileName);
};
// yes 버튼 클릭 시 
const yesClick = () => {
  generatePdf();
};

//corstest
const corstest = () => {
    corstestvue();
    corstestvuespring();
};

const requestvariable = ref('3');
const responseData = ref(null);
const corstestvue = async () => {
    try {
        console.log("들어왔음");
        const response = await axios.post(`http://localhost:8000/corstestvue/${requestvariable.value}`);
        responseData.value = response;
        console.log("api쐈음");
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
};
const corstestvuespring = async () => {
    try {
        console.log("들어왔음");
        const response = await axios.post(`http://localhost:8000/corstestvuespring/${requestvariable.value}`);
        responseData.value = response;
        console.log("api쐈음");
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
};

</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5> School Level </h5>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card"> 
                <Button @click="corstest()" label="cors 테스트" class="mr-2 mb-2"></Button>
                {{ responseData }}
                <Button @click="yesClick()" label="다운로드" icon="pi pi-download"  class="mr-2 mb-2"></Button>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card"> 
                <h5> 학습지 미리보기 </h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem'}" :pt="{wrapper: {style: {'border-right': '10px solid var(--surface-ground)'}}, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80'}"> 
                    <div id="testImage" ref="pdfAreaRef">
                        <div v-if="isImageExist" class="grid mx-2 my-4">
                            <div class="testItemBox col-12" style="aspect-ratio: 5/1;">
                                <div class="grid">
                                    <div class="col-12 mx-3 mt-3 logo">
                                        <img src="layout/images/logo-mmt4.png" alt="logo"/>
                                        <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl"> MMT</span>
                                        <span class="text-xs sm:text-base md:text-lg lg:text-xl xl:text-lg ml-auto px-5"> 문의 : contact.mmt.2024@gmail.com </span>
                                    </div>
                                    <div class="col-12">
                                        <div class="flex justify-content-between">
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl font-medium text-900 mx-2">
                                                {{ schoolLevel }} - {{ grade }} - {{ semester }}
                                            </span>
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl mx-2">{{ formattedDate }}</span>
                                        </div>
                                        <div class="flex justify-content-between">
                                            <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl text-900 font-medium mx-2">
                                                {{ testName }}
                                            </span>
                                            <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl text-900 font-medium mx-2">
                                                {{ userGrade }} {{ userDetail.userName }}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div v-for="(item, index) in testDetail" :key="index" class="testItemBox col-6" :style="computeAspectRatio(index+1)">
                                <div class="text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl overlay-text">{{ index + 1 }}</div>
                                <img :src="item.itemImagePath" alt="Item Image" class="fit-image"/>
                            </div>
                            <div v-for="i in (6-(testDetail.length%6))%6 " :key="'empty_' + i " class="testItemBox col-6" style="aspect-ratio: 1/1;">
                                같은 사이즈의 빈 이미지 넣기
                            </div>
                            <div class="col-12 text-4xl"> 정답 </div>
                            <div v-for="(item, index) in testDetail" :key="index" class="col-12">
                                <span>{{ index + 1 }}. </span>                        
                                <span v-html="renderItemAnswer(item.itemAnswer)"></span>
                            </div>
                        </div>
                        <div v-else class="grid mx-2 my-4">
                            <div class="testItemBox col-12" style="aspect-ratio: 5/1;">
                                <div class="grid">
                                    <div class="col-12 mx-3 mt-3 logo">
                                        <img src="layout/images/logo-mmt4.png" alt="logo"/>
                                        <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl"> MMT</span>
                                        <span class="text-xs sm:text-base md:text-lg lg:text-xl xl:text-lg ml-auto px-5"> 문의 : contact.mmt.2024@gmail.com </span>
                                    </div>
                                    <div class="col-12">
                                        <div class="flex justify-content-between">
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl font-medium text-900 mx-2">
                                                {{ schoolLevel }} - {{ grade }} - {{ semester }}
                                            </span>
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl mx-2">{{ formattedDate }}</span>
                                        </div>
                                        <div class="flex justify-content-between">
                                            <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl text-900 font-medium mx-2">
                                                {{ testName }}
                                            </span>
                                            <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl text-900 font-medium mx-2">
                                                {{ userGrade }} {{ userDetail.userName }}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div v-for="(item, index) in testDetail" :key="index" class="testItemBox col-6 flex align-items-center justify-content-center" :style="computeAspectRatio(index+1)">
                                <div class="text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl overlay-text">{{ index + 1 }}</div>
                                <div> 
                                    <div class="text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl text-800 flex align-items-center justify-content-center mb-2 mx-2"> {{ item.conceptName }}</div>
                                    <div class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl flex align-items-center justify-content-center"> 에 대한 문항입니다.</div>
                                </div>
                            </div>
                        </div>
                    </div>
                <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
        </div>
    </div>
<!-- <div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제1 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제2 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제3 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제4 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제5 </div>
<div class="testBox col-6" style="aspect-ratio: 40/35;"> 문제6 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제7 </div>
<div class="testBox col-6" style="aspect-ratio: 1/1;"> 문제8 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제9 </div>
<div class="testBox col-6" style="aspect-ratio: 1/1;"> 문제10 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제11 </div>
<div class="testBox col-6" style="aspect-ratio: 40/37;"> 문제12 </div>
<div class="testBox col-6" style="aspect-ratio: 5/4;"> 문제13 </div>
<div class="testBox col-6" style="aspect-ratio: 1/1;"> 문제14 </div> -->
</template>

<style scoped>

.testItemBox {
    position: relative;
    border: 1px solid black;
    padding: 5px;
}
.fit-image {
    max-width: 100%; /* 최대 너비를 부모 요소인 div의 크기에 맞게 조정합니다. */
    height: auto; /* 이미지의 가로세로 비율을 유지하면서 조정합니다. */
    display: block; /* 인라인 요소와의 공간을 없애기 위해 블록 요소로 변경합니다. */
}
.overlay-text {
    position: absolute;
    top: 8%;
    left: 6%;
}
/* .logo-img {
    height: auto;
    max-width: 5%;
    margin-right: 0.5rem;
} */
.logo {
    display: flex;
    align-items: center;
    color: var(--surface-900);
    font-size: 1.5rem;
    font-weight: 500;
    width: 100%;
    border-radius: 12px;
    padding: 5px;
}
.logo img {
    height: auto;
    max-width: 5%;
    margin-right: 0.5rem;
}
/* .flex-text {
  font-size: 1.5rem;
}
@media (min-width: 600px) {
  .flex-text {
    font-size: 2rem; 
  }
}
@media (min-width: 1200px) {
  .flex-text {
    font-size: 2.5rem; 
  }
} */
</style>
