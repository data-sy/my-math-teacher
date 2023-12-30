<script setup>
// 링크 전 화면에서 데이터 가져오는 것 해보기

import { ref, watch } from 'vue';
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/api.js';
import levelDic from '@/assets/data/level.json';

const api = useApi();

// schoolLevel
const selectButtonLevel = ref(null);
const selectButtonLevels = ref([{ name: '초등' }, { name: '중등' }, { name: '고등' }]);
// gradeLevel
const listboxLevel = ref(null);
const listboxLevels = ref([]);
const listboxTestsAll = ref(null);
watch(selectButtonLevel, async (newValue) => {
    if (newValue.name === '초등') {
        listboxLevels.value = levelDic['초등'];
    } else if (newValue.name === '중등') {
        listboxLevels.value = levelDic['중등'];
    } else if (newValue.name === '고등') {
        listboxLevels.value = levelDic['고등'];
    }
    try {
        const endpoint = `/tests/school-level/${newValue.name}`;
        const response = await api.get(endpoint);
        listboxTestsAll.value = response
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
});
const listboxTest = ref(null);
const listboxTests = ref([]);

// 학습지 목록
watch(listboxLevel, (newValue) => {
    const grade = newValue.grade;
    const semester = newValue.semester;
    if (listboxTestsAll.value) {
        const filteredTests = listboxTestsAll.value.filter(test => {
            return test.testGradeLevel === grade && test.testSemester === semester;
        });
        listboxTests.value = filteredTests
    }

});

// 학습지 미리보기
const testDetail = ref([]);
watch(listboxTest, async (newValue) => {
    try {
        const endpoint = `/tests/${newValue.testId}`;
        const response = await api.get(endpoint);
        testDetail.value = response
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
});
// 답안 원문자 표현
const renderItemAnswer = (text) => {
    return text;
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
// yes 버튼 클릭 시 진단학습지 다운로드
// 다운로드
// const download = () => {
//   // download 동작 정의
//   const imageSrc = $refs.image.src;
//   const link = document.createElement('a');
//   link.href = imageSrc;
//   link.download = 'image.jpg'; // 다운로드될 파일명
//   link.click();
// };
// create api
const postData = async () => {
  try {
    const endpoint = `/tests/${listboxTest.testId}`;
    await api.post(endpoint);
  } catch (err) {
    console.error(`POST ${endpoint} failed:`, err);
  }
};
const yesClick = () => {
  closeConfirmation(); // 첫 번째 이벤트 핸들러에서 실행할 동작
  goToHome();
//   download(); // 두 번째 이벤트 핸들러에서 실행할 동작
  // create api 추가
  postData();
};

</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5> School Level </h5>
                <SelectButton v-model="selectButtonLevel" :options="selectButtonLevels" optionLabel="name" />
            </div>
            <div class="card">
                <h5> Grade Level </h5>
                <Listbox v-model="listboxLevel" :options="listboxLevels" optionLabel="name" />
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card"> 
                <h5> 학습지 목록 </h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" :filter="true"/>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card"> 
                <h5> 학습지 미리보기 </h5>
                <!--스크롤 기능 추가하기-->
                <div class="grid" >
                    <!-- card는 영역을 보기 위해 임시적으로 사용-->
                    <div class="card col-12" style="height: calc(8vw);"> 유저 이름, 학습지 이름, 현재 날짜, (이거는 학원 학습지 틀 참조하기 - 촬영!)</div>
                    <div v-for="(item, index) in testDetail" :key="index" class="card col-6">
                        <img :src="item.itemImagePath" alt="Item Image"  class="fit-image"/>
                        <div v-if="index > 5" style="height: calc(4vw);"> 크기 맞추기 위해 여백 만들기 </div>
                    </div>
                    <div v-for="i in (6-(testDetail.length%6))%6 " :key="'empty_' + i " style="height: calc(23vw);" class="card col-6">
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
                </div>
            </div>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToHome" label="이전" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8">빈공간</div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button label="다운로드" icon="pi pi-download" class="mr-2 mb-2" @click="openConfirmation" />
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
