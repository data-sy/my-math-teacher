<script setup>
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import levelDic from '@/assets/data/level.json';

const router = useRouter()
const api = useApi();
const { htmlToPdf } = useHtmlToPdf();

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
const testId = ref(null);
watch(listboxTest, async (newValue) => {
    testId.value = newValue.testId;

    try {
        const endpoint = `/tests/${testId.value}`;
        const response = await api.get(endpoint);
        testDetail.value = response
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
});
// 답안 원문자 표현 (HTML 엔티티)
const renderItemAnswer = (text) => {
    return text;
};
// pdf 다운로드
const pdfAreaRef = ref(null);
const generatePdf = () => {
  htmlToPdf(pdfAreaRef.value, 'MyFile');
};
// 유저-학습지 DB에 저장
const createDiagTest = async () => {
  try {
    const endpoint = `/tests/${testId.value}`;
    await api.post(endpoint);
  } catch (err) {
    console.error(`POST ${endpoint} failed:`, err);
  }
};
// 학습지를 누르지 않고 [다운로드]버튼을 누르면, 학습지 목록에서 학습지를 먼저 골라달라고 안내
const popup = ref(null);
const toast = useToast();
const confirmPopup = useConfirm();
const confirm = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '학습지 목록에서 학습지를 선택해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '학습지를 선택하면 다운로드할 수 있습니다.', life: 3000 });
        },
    });
};  
// '이전' 버튼 (홈으로)
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
// yes 버튼 클릭 시 
const yesClick = () => {
  closeConfirmation();
  generatePdf();
  createDiagTest();
  goToHome();
};

</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between">
                    <div>
                        <div class="text-900 font-medium text-xl mb-3"> 여기는 진단학습지를 출력하는 곳이야. </div>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-600 font-medium mb-3"> 1. [School Level]에서 원하는 학교군 선택하기 </span>
                        <span class="block text-600 font-medium mb-3"> 2. [Gradel Level]에서 원하는 학년-학기 선택하기 </span>
                        <span class="block text-600 font-medium"> 3. [학습지 목록]에서 원하는 학습지 선택하기 </span>
                        <ul style="list-style-type: disc;">
                            <li class=mb-2> 소단원을 기준으로 진단학습지가 준비되어 있어.</li>
                        </ul>
                        <span class="block text-600 font-medium mb-3"> 4. [학습지 미리보기]로 문제 확인 </span>
                        <span class="block text-600 font-medium"> 5. [다운로드] 버튼 누르기 </span>
                        <ul style="list-style-type: disc;">
                            <li> pdf 파일이 저장되고 홈 화면으로 이동할 거야.</li>
                        </ul>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-red-500 font-medium"> 준비된 학습지 : 고등 -> 수학(상) -> 복소수와 이차방정식(1)~(5) </span>
                    </div>
                </div>
            </div>
        </div>
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
                <ScrollPanel :style="{ width: '100%', height: '35rem'}" :pt="{wrapper: {style: {'border-right': '10px solid var(--surface-ground)'}}, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80'}"> 
                    <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" :filter="true"/>
                <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card"> 
                <h5> 학습지 미리보기 </h5>
                <!--스크롤 기능 추가하기-->
                <div class="grid" id="testImage" ref="pdfAreaRef">
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
            <Button @click="goToHome" label="이전" class="mr-2 mb-5"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8"></div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <Button v-if="testId == null" ref="popup" @click="confirm($event)" label="학습지를 선택하세요." icon="pi pi-download" class="mr-2 mb-2"></Button>
            <Button v-else @click="openConfirmation" label="다운로드" icon="pi pi-download"  class="mr-2 mb-2" />
                <Dialog header="다음 학습지를 다운로드 하시겠습니까?" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                    <div class="text-600 font-semibold px-3 py-2"> {{ listboxTest.testSchoolLevel }} - {{ listboxTest.testGradeLevel }} - {{ listboxTest.testSemester }} </div>
                    <div class="text-600 font-semibold px-3 py-1"> &quot;{{ listboxTest.testName }} &quot; 학습지 </div>
                    <template #footer>
                        <Button label="No" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                        <Button label="Yes" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
                    </template>
                </Dialog>
        </div>     
    </div>
</template>
