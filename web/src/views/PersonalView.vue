<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useStore } from 'vuex';

const store = useStore();
const router = useRouter();
const api = useApi();
const { htmlToPdf } = useHtmlToPdf();

// [출제하기] 버튼에 준비중 띄워둠
const confirmPopup = useConfirm();
const confirm4 = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '이 기능은 준비중입니다. 조금만 기다려주세요!',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            // toast.add({ severity: 'info', summary: 'Confirmed', detail: '', life: 3000 });
        },
    });
};

const dataToSend = history.state.dataToSend;
const receivedData = ref('');
const isLoggedIn = ref(false);
const listboxTest = ref(null);
const listboxTests = ref([]);
// 학습지 목록
onMounted(async() => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null;
    watch(() => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    )
    if (isLoggedIn.value) {
        try {
            const endpoint = 'tests/user/is-record';
            const response = await api.get(endpoint);
            listboxTests.value = response;
            if (dataToSend) {
                receivedData.value = dataToSend;
                const selectedTestData = listboxTests.value.find((item) => item.userTestId === receivedData.userTestId);
                if (selectedTestData) {
                    listboxTest.value = selectedTestData;
                }
            }
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    } else {
        console.log("사용자가 로그인하지 않았습니다. 학습지 목록을 건너뜁니다.");
    }
});
const testId = ref(null);
watch(listboxTest, (newValue) => {
    testId.value = newValue.testId;  
});

// 조건
const inputNumberValue = ref(10);
const radioValue1 = ref(null);
const radioValue2 = ref(null);

// [출제하기] 버튼 클릭 시 그에 따른 get api 받기
const isSet = ref(false);
const setTest = () => {
    isSet.value = !isSet.value;
};

// 학습지 미리보기 
// const testDetail = ref([]);
// const testId = ref(null);
// watch(listboxTest, async (newValue) => {
//     testId.value = newValue.testId;
//     try {
//         const endpoint = `/tests/detail/${testId.value}`;
//         const response = await api.get(endpoint);
//         testDetail.value = response
//     } catch (err) {
//         console.error('데이터 생성 중 에러 발생:', err);
//     }    
// });
// 답안 원문자 표현 (HTML 엔티티)
const renderItemAnswer = (text) => {
    return text;
};
// pdf 다운로드
const pdfAreaRef = ref(null);
const generatePdf = () => {
  htmlToPdf(pdfAreaRef.value, 'MyFile');
};
// api : testDB, userTestDB, testItemDB 모두 쏴야 함
// 유저-학습지 DB에 저장 => createPersonalTest로 수정
// const createDiagTest = async () => {
//   try {
//     const endpoint = `/tests/${testId.value}`;
//     await api.post(endpoint);
//   } catch (err) {
//     console.error(`POST ${endpoint} failed:`, err);
//   }
// };
// 조건를 누르지 않고 [다운로드]버튼을 누르면, 조건을 먼저 누르라고 안내
const popup = ref(null);
const toast = useToast();
const confirm = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '학습지 목록에서 학습지를 선택해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '학습지를 선택하면 해당 학습지 분석 결과에 맞게 출제됩니다.', life: 3000 });
        },
    });
};
// 로그인 하지 않고 [다운로드] 버튼을 누르면, 회원가입이나 로그인을 먼저 해달라고 안내
const confirm2 = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '로그인 혹은 회원가입을 해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 맞춤학습지를 출제할 수 있습니다.', life: 3000 });
        },
    });
};
// [출제하기]를 누르지 않고 [다운로드] 버튼을 누르면, [출제하기] 버튼을 먼저 눌러달라고 안내
const confirm3 = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '출제하기 버튼을 눌러주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '출제하기 버튼을 누르면 맞춤학습지를 출제할 수 있습니다.', life: 3000 });
        },
    });
};
// '이전' 버튼 (홈으로 또는 분석결과보기로)
const goToHome = () => {
  try {
    router.go(-1) // 또는 router.back()
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
//   createDiagTest(); => create api 3개 추가
  goToHome();
};

</script>

<template>
    <div class="grid p-fluid">
        <div class="text-red-400 font-medium text-8xl mb-3 px-3">준비중인 페이지입니다.</div>
        <div class="col-12 text-center">
            <div v-if="!isLoggedIn" class="text-orange-500 font-medium text-3xl">로그인이 필요한 페이지 입니다.</div>
        </div>
        <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between">
                    <div>
                        <div class="text-900 font-medium text-xl mb-3">여기서는 너에게 맞는 '맞춤학습지'를 만들 수 있어</div>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-600 font-medium"> 1. [분석 결과 학습지 목록]에서 학습지 선택하기 </span>
                        <ul style="list-style-type: disc">
                            <li class="mb-2">선택한 학습지 결과를 바탕으로 맞춤학습지를 출제해 줄 거야</li>
                        </ul>
                        <span class="block text-600 font-medium mb-3"> 2. [학습지 조건]에서 조건 선택하기 </span>
                        <ul style="list-style-type: disc">
                            <li class="mb-2">각 조건에 대한 설명은 <i class="pi pi-question-circle"></i> 을 참고해.</li>
                            <li class="mb-2">"학습지 코멘트"창에 학습지에 대한 ㅇㅇ을 쓸 수 있어</li>
                            <li class="mb-2">원하는 조건을 선택하고 [출제하기] 버튼을 누르면 해당 조건에 맞는 학습지가 [학습지 미리보기]에 뜰 거야.</li>
                        </ul>
                        <span class="block text-600 font-medium"> 3. [학습지 미리보기]에서 문항 수정하기 </span>
                        <ul style="list-style-type: disc">
                            <li class="mb-2">[유사 문제] 같은 개념에 대한 다른 문제를 출제할 수 있어</li>
                            <li class="mb-2">[문항 상세보기] 어떤 개념에 대한 문항인지 설명을 볼 수 있어</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5>학습지 목록</h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" />
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5>학습지 조건</h5>
                <div class="mb-4">
                    <label for="number" class="block text-900 text-xl font-medium mb-3">문항 수</label>
                    <InputNumber v-model="inputNumberValue" inputId="minmax-buttons" mode="decimal" showButtons :min="6" :max="30"></InputNumber>
                </div>
                <label for="number" class="block text-900 text-xl font-medium mb-3">맞춤 유형</label>
                <div class="grid">
                    <div class="col-12 md:col-6">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="wrong" name="category" value="wrong" v-model="radioValue1" />
                            <label for="wrong">오답 문항 위주</label>
                        </div>
                    </div>
                    <div class="col-12 md:col-6">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="prerequisite" name="category" value="prerequisite" v-model="radioValue1" />
                            <label for="prerequisite">선수 지식 위주</label>
                        </div>
                    </div>
                </div>
                <label for="number" class="block text-900 text-xl font-medium mb-2">문항 재출제</label>
                <div class="grid">
                    <div class="col-12 md:col-4">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="nothing" name="reExam" value="nothing" v-model="radioValue2" />
                            <label for="nothing">없음</label>
                        </div>
                    </div>
                    <div class="col-12 md:col-4">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="wrong" name="reExam" value="wrong" v-model="radioValue2" />
                            <label for="wrong">오답 문항</label>
                        </div>
                    </div>
                    <div class="col-12 md:col-4">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="all" name="reExam" value="prerequisite" v-model="radioValue2" />
                            <label for="all">전체 문항</label>
                        </div>
                    </div>
                </div>
                <Button @click="confirm4($event)" label="출제하기" class="mr-2 mb-5"></Button>
                <!-- 여기도 isLoggedIn, testId 에 따라 조건문 줘야 해-->
                <!-- <Button @click="setTest" label="출제하기" class="mr-2 mb-5"></Button> -->
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                <h5>학습지 미리보기</h5>
                학습지 이름은 notnull - validation 활용
            </div>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToHome" label="이전" class="mr-2 mb-5"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8"></div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <Button v-if="!isLoggedIn" ref="popup" @click="confirm2($event)" label="로그인을 해주세요." icon="pi pi-download" class="mr-2 mb-2"></Button>
            <Button v-else-if="testId == null" ref="popup" @click="confirm($event)" label="학습지를 선택하세요." class="mr-2 mb-2"></Button>
            <Button v-else-if="!isSet" ref="popup" @click="confirm3($event)" label="출제하기를 누르세요." class="mr-2 mb-2"></Button>
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
