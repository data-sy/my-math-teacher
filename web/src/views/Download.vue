<script setup>
import { onMounted, reactive, ref, watch, nextTick  } from 'vue';
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';

const api = useApi();

// 학습지 미리보기
const testDetail = ref([]);
const getTest = async () => {
    try {
        const endpoint = "/tests/491";
        const response = await api.get(endpoint);
        testDetail.value = response
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
};
// 답안 원문자 표현
const renderItemAnswer = (text) => {
    return text;
};

// 다운로드
const pdfAreaRef = ref(null);
const { htmlToPdf } = useHtmlToPdf();
const generatePdf = () => {
  // pdf 생성을 위한 ref를 전달하여 호출
  htmlToPdf(pdfAreaRef.value, 'MyFile');
};



// '이전' 버튼 (홈으로)
const router = useRouter()
const goToHome = () => {
  try {
    router.push({ path: '/' }); 
  } catch (error) {
    console.error('에러 발생:', error);
  }
};

// 다운로드 확인 창
const displayConfirmation = ref(false);
const openConfirmation = () => {
    displayConfirmation.value = true;
};
const closeConfirmation = () => {
    displayConfirmation.value = false;
};

// 다운로드
// const imageUrl = 'demo/images/galleria/galleria11.jpg';
// const imageRef = ref(null);

// 클릭 시 담길 값이야 (ref에서 꺼낼때는 value 사용하기)
const testId = 123;
const postData = async () => {
  try {
    const endpoint = `/tests/${testId}`;
    await api.post(endpoint);
  } catch (err) {
    console.error(`POST ${endpoint} failed:`, err);
  }
};

// 추가) testid가 선택되기 전까지 다운로드 버튼 비활성화 
const yesClick = () => {
  closeConfirmation();
  generatePdf();
  postData();
  goToHome();
};

</script>


<template>
    <div class="grid p-fluid">
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
              <button @click="getTest">491 학습지보기</button>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <button @click="generatePdf">Generate PDF</button>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
              <h5> 학습지 미리보기 </h5>
                <div class="grid" id="testImage" ref="pdfAreaRef">
                  <div class="card col-12" style="height: calc(8vw);"> 유저 이름, 학습지 이름, 현재 날짜, (이거는 학원 학습지 틀 참조하기 - 촬영!)</div>
                    <div v-for="(item, index) in testDetail" :key="index" class="col-6">
                        <img :src="item.itemImagePath" alt="Item Image"  class="fit-image"/>
                        <div v-if="index > 5" style="height: calc(4vw);"> 크기 맞추기 위해 여백 만들기 </div>
                    </div>
                    <div v-for="i in (6-(testDetail.length%6))%6 " :key="'empty_' + i " style="height: calc(23vw);" class="col-6">
                        같은 사이즈의 빈 이미지 넣기
                    </div>
                    <!-- <div>
                        src/assets에 담아뒀을 때 목록 내 모든 이미지 보기 테스트
                        <img :src="img" v-for="img of images" :key="img" class="card col-6"/>
                    </div> -->
                    <div>정답도 맘에 드는 템플릿 가져와서 사용하기</div>
                    <div v-for="(item, index) in testDetail" :key="index" class="col-12">
                        <!-- index+1 표시 -->
                        <span>{{ index + 1 }}. </span>                        
                        <!-- itemAnswer 표시 -->
                        <span v-html="renderItemAnswer(item.itemAnswer)"></span>
                    </div>
                    <!-- <div class ="col-12 xl:col-6">
                        <div class="flex justify-content-center">
                            <Image :src="imageUrl" ref="imageRef" alt="Image" width="150" preview />
                        </div>
                        <div class="flex justify-content-center">
                            <Image :src="imageUrl" ref="imageRef" alt="Image" width="150" preview />
                        </div>
                    </div>
                    <div class ="col-12 xl:col-6">
                        <div class="flex justify-content-center">
                            <Image :src="imageUrl" ref="imageRef" alt="Image" width="150" preview />
                        </div>
                        <div class="flex justify-content-center">
                            <Image :src="imageUrl" ref="imageRef" alt="Image" width="150" preview />
                        </div>
                    </div>     -->
                </div>             
            </div>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToHome" label="이전" class="mr-2 mb-2"></Button>
        </div>
        <div class="col">빈</div>
        <div class="col">공</div>
        <div class="col">간</div>
        <div class="col">!</div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button label="다운로드" icon="pi pi-check" class="mr-2 mb-2" @click="openConfirmation" />
            <Dialog header="학습지 다운로드" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="flex align-items-center justify-content-center">
                    <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                    <span>Are you sure you want to proceed?</span>
                </div>
                <template #footer>
                    <Button label="No" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="Yes" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
                </template>
            </Dialog>
        </div>
    </div>
</template>