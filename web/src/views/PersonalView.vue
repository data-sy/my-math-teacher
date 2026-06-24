<script setup>
import { ref, onMounted, watch, computed } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';
import TitleService from '@/service/TitleService';
import { VMarkdownView } from 'vue3-markdown';
import 'vue3-markdown/dist/style.css';

const store = useStore();
const router = useRouter();
const api = useApi();
const { htmlToPdf } = useHtmlToPdf();

const logoUrl = computed(() => {
    return 'images/logo/logo-mmt4.png';
});

// 유저 정보
const isLoggedIn = ref(false);
const userDetail = ref({
    userName: '',
    userBirthdate: ''
});
const userGrade = ref('');
const confirmPopup = useConfirm();

const dataToSend = history.state.dataToSend;
const receivedData = ref('');
const listboxTest = ref(null);
const listboxTests = ref([]);
// 학습지 목록
onMounted(async () => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null;
    watch(
        () => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    );
    // 로그인 했을 때는 user의 학습지
    if (isLoggedIn.value) {
        // PDF 학습지 헤더에 표시할 사용자 이름·학년 (RecordView와 동일 패턴)
        try {
            const userResponse = await api.get('/api/v1/users');
            userDetail.value = userResponse;
            userGrade.value = TitleService.calculateGrade(userDetail.value.userBirthdate);
        } catch (err) {
            console.error('사용자 정보 조회 중 에러 발생:', err);
        }
        try {
            const endpoint = '/api/v1/tests/user/is-record';
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
    // 로그인 안 했을 때는 샘플 학습지
    } else {
        console.log('사용자가 로그인하지 않았습니다. 샘플 학습지 목록을 제공합니다.');
        try {
            const endpoint = '/api/v1/tests/sample/is-record';
            const response = await api.get(endpoint);
            listboxTests.value = response.map((item) => {
                return {
                    ...item,
                    testName: `샘플-${item.testName}`
                };
            });
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }   
    }
});

// 맞춤학습지의 근간이 될 학습지 선택
const userTestId = ref(null);
watch(listboxTest, (newValue) => {
    // 베이스 학습지 선택/해제 시 출제 상태 초기화 — [출제하기]를 다시 눌러야 다운로드 가능
    isSet.value = false;
    testDetail.value = [];
    if (newValue !== null) {
        userTestId.value = newValue.userTestId;
        testName.value = newValue.testName;
    } else {
        // 선택 해제(Listbox 토글) 시 다운로드 게이트를 '학습지를 선택하세요.'로 되돌림
        // → 다운로드 확인 Dialog의 listboxTest null 역참조(크래시) 방지
        userTestId.value = null;
        testName.value = '';
    }
});


// 맞춤 학습지 조건 (Scope B — 출제 API 파라미터로 전달)
//  radioValue1 = 맞춤 유형: 'wrong'(오답 위주=depth0) | 'prerequisite'(선수지식 위주=depth1~2)
//  radioValue2 = 재출제: 'nothing' | 'wrong'(원래 오답 문항) | 'all'(원래 응시 문항 전체). null=재출제 없음
//  inputNumberValue = 신규 문항 수(6~30)
const inputNumberValue = ref(10);
const radioValue1 = ref(null);
const radioValue2 = ref(null);
// 출제 완료 여부 = 다운로드 게이트. 베이스 학습지 선택 후 [출제하기]를 누르면 true
const isSet = ref(false);
/////////////////////////////////////////////////////////////////////////////////
// 추가구현 해야 할 부분
// (+) 프론트 단에서 조건들을 만족해야 출제하기 버튼 활성화하기 (클릭하면 조건 채워달라고 알람)
/////////////////////////////////////////////////////////////////////////////////

// 맞춤 학습지 미리보기 - [출제하기] 버튼 누르면 조건에 맞는 문항 제시
const testDetail = ref([]);
const getPersonalItems = async () => {
    if (userTestId.value === null) return;
    // 맞춤 유형은 필수 — 미선택 시 출제하지 않고 안내
    if (!radioValue1.value) {
        toast.add({ severity: 'warn', summary: '맞춤 유형을 선택해주세요', detail: '오답 문항 위주 또는 선수 지식 위주 중 하나를 선택하면 출제됩니다.', life: 3000 });
        return;
    }
    {
        try {
            const params = new URLSearchParams({
                userTestId: userTestId.value,
                category: radioValue1.value,
                count: inputNumberValue.value
            });
            // 재출제 미선택(null)은 '없음'(원본 미포함)으로 처리 — 파라미터 생략
            if (radioValue2.value) params.set('reExam', radioValue2.value);
            const endpoint = `/api/v1/items/personal?${params.toString()}`;
            const response = await api.get(endpoint);
            testDetail.value = response.map((item) => {
                return {
                    ...item,
                    itemImagePath: "/images/items/empty001.jpg"
                };
            });
            isSet.value = true;
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    }
}

const testName = ref('');

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
const isLatex = (answer) => {
    return !answer.includes('&#');
};
// pdf 다운로드
const pdfAreaRef = ref(null);
const generatePdf = () => {
    const fileName = `MMT_${testName.value}`;
    htmlToPdf(pdfAreaRef.value, fileName);
};

// api는 testDB, userTestDB, testItemDB 모두 쏴야 함
// 새 학습지 생성
    // testDB: 새 학습지 생성 => 새로 생성된 학습지 번호 리턴
    // 그 번호 받아서
    // testItemDB :학습지와 문항 연결
// 학생의 학습지로 저장
    // userTestDB

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
        }
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
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 맞춤학습지를 다운로드할 수 있습니다.', life: 3000 });
        }
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
        }
    });
};
// '이전' 버튼 (홈으로 또는 분석결과보기로)
const goToHome = () => {
    try {
        router.go(-1); // 또는 router.back()
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
    // create api 3개 추가
    goToHome();
};
</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5>맞춤학습지의 근간이 될 학습지 선택</h5>
                <div>
                    <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" />
                </div>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5> 맞춤학습지 조건</h5>
                <div class="mb-3 mt-4">
                    <p class="text-700 text-lg m-0 mb-1">진단 결과(오답·선수 지식)를 바탕으로 맞춤 출제됩니다.</p>
                    <p class="text-500 m-0">맞춤 유형을 선택하고 문항 수·재출제 조건을 설정해 출제하세요.</p>
                </div>
                <div class="mb-4">
                    <label for="number" class="block text-900 text-xl font-medium mb-3">문항 수 (6 ~ 30)</label>
                    <InputNumber v-model="inputNumberValue" inputId="minmax-buttons" mode="decimal" showButtons :min="6" :max="30"></InputNumber>
                </div>
                <label for="number" class="block text-900 text-xl font-medium mb-3">맞춤 유형</label>
                <div class="grid">
                    <div class="col-12 md:col-6">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="categoryWrong" name="category" value="wrong" v-model="radioValue1" />
                            <label for="categoryWrong">오답 문항 위주</label>
                        </div>
                    </div>
                    <div class="col-12 md:col-6">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="categoryPrerequisite" name="category" value="prerequisite" v-model="radioValue1" />
                            <label for="categoryPrerequisite">선수 지식 위주</label>
                        </div>
                    </div>
                </div>
                <label for="number" class="block text-900 text-xl font-medium mb-2">문항 재출제</label>
                <div class="grid">
                    <div class="col-12 md:col-4">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="reExamNothing" name="reExam" value="nothing" v-model="radioValue2" />
                            <label for="reExamNothing">없음</label>
                        </div>
                    </div>
                    <div class="col-12 md:col-4">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="reExamWrong" name="reExam" value="wrong" v-model="radioValue2" />
                            <label for="reExamWrong">오답 문항</label>
                        </div>
                    </div>
                    <div class="col-12 md:col-4">
                        <div class="field-radiobutton mb-0">
                            <RadioButton id="reExamAll" name="reExam" value="all" v-model="radioValue2" />
                            <label for="reExamAll">전체 문항</label>
                        </div>
                    </div>
                </div>
                <div class="mt-5">
                    <Button label="출제하기" class="mr-2 mb-5" :disabled="userTestId == null" @click="getPersonalItems"></Button>
                </div>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                <h5>맞춤 학습지 미리보기</h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem' }" :pt="{ wrapper: { style: { 'border-right': '10px solid var(--surface-ground)' } }, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80' }">
                    <div id="testImage" ref="pdfAreaRef">
                        <div class="grid mx-2 my-4">
                            <div class="testItemBox col-12" style="aspect-ratio: 5/1">
                                <div class="grid">
                                    <div class="col-12 mx-3 mt-3 logo">
                                        <img :src="logoUrl" alt="logo" />
                                        <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl"> MMT</span>
                                        <span class="text-xs sm:text-base md:text-lg lg:text-xl xl:text-lg ml-auto px-5"> 문의 : contact.mmt.2024@gmail.com </span>
                                    </div>
                                    <div class="col-12">
                                        <div class="flex justify-content-between">
                                            <!-- <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl font-medium text-900 mx-2"> {{ schoolLevel }} - {{ grade }} - {{ semester }} </span> -->
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl mx-2">{{ formattedDate }}</span>
                                        </div>
                                        <div class="flex justify-content-between">
                                            <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl text-900 font-medium mx-2">
                                                {{ testName }}
                                            </span>
                                            <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl text-900 font-medium mx-2"> {{ userGrade }} {{ userDetail.userName }} </span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div v-for="(item, index) in testDetail" :key="index" class="testItemBox col-6 flex align-items-center justify-content-center" :style="computeAspectRatio(index + 1)">
                                <div class="text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl overlay-text">
                                    <span> {{ index + 1 }} </span>
                                    <span class="text-xs sm:text-base md:text-xl lg:text-3xl xl:text-xl mx-3"> {{ item.schoolLevel}} - {{ item.gradeLevel }} - {{ item.semester }}</span>
                                </div>
                                <div>
                                    <div class="flex align-items-center justify-content-center mb-2 mx-2">
                                        <VMarkdownView :content="item.conceptName" class="text-lg sm:text-xl md:text-2xl lg:text-4xl xl:text-2xl text-800"></VMarkdownView>
                                    </div>
                                    <div class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl flex align-items-center justify-content-center">에 대한 문항입니다.</div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToHome" label="이전" class="mr-2 mb-5"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8"></div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <Button v-if="!isLoggedIn" ref="popup" @click="confirm2($event)" label="로그인 후 다운로드" icon="pi pi-download" class="mr-2 mb-2"></Button>
            <Button v-else-if="userTestId == null" ref="popup" @click="confirm($event)" label="학습지를 선택하세요." class="mr-2 mb-2"></Button>
            <Button v-else-if="!isSet" ref="popup" @click="confirm3($event)" label="출제하기를 누르세요." class="mr-2 mb-2"></Button>
            <Button v-else @click="openConfirmation" label="다운로드" icon="pi pi-download" class="mr-2 mb-2" />
            <Dialog header="다음 학습지를 다운로드 하시겠습니까?" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="text-600 font-semibold px-3 py-2">{{ listboxTest?.testSchoolLevel }} - {{ listboxTest?.testGradeLevel }} - {{ listboxTest?.testSemester }}</div>
                <div class="text-600 font-semibold px-3 py-1">&quot;{{ listboxTest?.testName ?? testName }} &quot; 학습지</div>
                <template #footer>
                    <Button label="아니오" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="예" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
                </template>
            </Dialog>
        </div>
    </div>
</template>

<style scoped>
.test-title {
    position: relative;
    border: 1px solid black;
    padding: 5px;
}
.testItemBox {
    position: relative;
    /* border: 1px solid black; */
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
</style>
