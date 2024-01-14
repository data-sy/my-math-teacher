<script setup>
import { ref, watch, onMounted } from 'vue';
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';

const api = useApi();
const { htmlToPdf } = useHtmlToPdf();

// 학습지 미리보기
const testDetail = ref([]);
onMounted(async() => {
    try {
        const endpoint = '/tests/detail/491';
        const response = await api.get(endpoint);
        testDetail.value = response
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
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
  htmlToPdf(pdfAreaRef.value, 'MyFile');
};
// yes 버튼 클릭 시 
const yesClick = () => {
  generatePdf();
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
                <Button @click="yesClick()" label="다운로드" icon="pi pi-download"  class="mr-2 mb-2"></Button>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card"> 
                <h5> 학습지 미리보기 </h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem'}" :pt="{wrapper: {style: {'border-right': '10px solid var(--surface-ground)'}}, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80'}"> 
                    <div id="testImage" ref="pdfAreaRef">
                        <div class="grid mx-2 my-4">
                            <div class="testItemBox col-12" style="aspect-ratio: 5/1;"> 제목 </div>
                            <div v-for="(item, index) in testDetail" :key="index" class="testItemBox col-6" :style="computeAspectRatio(index+1)">
                                <div class="text-xl sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl overlay-text">{{ index + 1 }}</div>
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
                        </div>
                    </div>
                <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
        </div>
    </div>
</template>

<style scoped>

.testItemBox {
    position: relative;
    border: 1px solid black;
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
</style>