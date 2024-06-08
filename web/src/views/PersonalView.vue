<script setup>
import { ref, onMounted, watch, computed } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';
import title from '@/composables/title.js';
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
        }
    });
};

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
    } else {
        console.log('사용자가 로그인하지 않았습니다. 학습지 목록을 건너뜁니다.');
    }
});

// 조건
const inputNumberValue = ref(10);
const radioValue1 = ref(null);
const radioValue2 = ref(null);

// 학습지 미리보기
const testDetail = ref([]);
const testId = ref(null);
const isImageExist = ref(false);
const testName = ref('');
watch(listboxTest, async (newValue) => {

});
// 날짜
const formattedDate = ref('');
const updateDate = () => {
    formattedDate.value = title.updateDate();
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
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 맞춤학습지를 출제할 수 있습니다.', life: 3000 });
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
        <div class="col-12 text-center">
            <div class="text-orange-500 font-medium text-3xl">준비중인 페이지 입니다. </div>
            <!-- <div v-if="!isLoggedIn" class="text-orange-500 font-medium text-3xl">로그인이 필요한 페이지 입니다.</div> -->
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5>맞춤학습지의 근간이 될 학습지 선택</h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" />
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5> 맞춤학습지 조건</h5>
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
                <h5>맞춤 학습지 미리보기</h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem' }" :pt="{ wrapper: { style: { 'border-right': '10px solid var(--surface-ground)' } }, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80' }">
                    <div id="testImage" ref="pdfAreaRef">
                        <div v-if="isImageExist" class="grid mx-2 my-4">
                            <div class="test-title col-12" style="aspect-ratio: 5/1">
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
                            <div v-for="(item, index) in testDetail" :key="index" class="testItemBox col-6" :style="computeAspectRatio(index + 1)">
                                <div class="text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl overlay-text">{{ index + 1 }}</div>
                                <img :src="item.itemImagePath" alt="Item Image" class="fit-image" />
                            </div>
                            <div v-for="i in (6 - (testDetail.length % 6)) % 6" :key="'empty_' + i" class="testItemBox col-6" style="aspect-ratio: 1/1"></div>
                            <div class="col-12 text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl">정답</div>
                            <div v-for="(item, index) in testDetail" :key="index" class="col-12">
                                <VMarkdownView v-if="isLatex(item.itemAnswer)" :content="index + 1 + '.' + item.itemAnswer" class="text-xs sm:text-xs md:text-base lg:text-base xl:text-base"></VMarkdownView>
                                <span v-else v-html="index + 1 + '. ' + renderItemAnswer(item.itemAnswer)" class="text-xs sm:text-xs md:text-base lg:text-base xl:text-base text-800"></span>
                            </div>
                        </div>
                        <div v-else class="grid mx-2 my-4">
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
                                <div class="text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl overlay-text">{{ index + 1 }}</div>
                                <div>
                                    <div class="flex align-items-center justify-content-center mb-2 mx-2">
                                        <VMarkdownView :content="item.conceptName" class="text-lg sm:text-2xl md:text-4xl lg:text-6xl xl:text-4xl text-800"></VMarkdownView>
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
            <Button v-if="!isLoggedIn" ref="popup" @click="confirm2($event)" label="로그인을 해주세요." icon="pi pi-download" class="mr-2 mb-2"></Button>
            <Button v-else-if="testId == null" ref="popup" @click="confirm($event)" label="학습지를 선택하세요." class="mr-2 mb-2"></Button>
            <Button v-else-if="!isSet" ref="popup" @click="confirm3($event)" label="출제하기를 누르세요." class="mr-2 mb-2"></Button>
            <Button v-else @click="openConfirmation" label="다운로드" icon="pi pi-download" class="mr-2 mb-2" />
            <Dialog header="다음 학습지를 다운로드 하시겠습니까?" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="text-600 font-semibold px-3 py-2">{{ listboxTest.testSchoolLevel }} - {{ listboxTest.testGradeLevel }} - {{ listboxTest.testSemester }}</div>
                <div class="text-600 font-semibold px-3 py-1">&quot;{{ listboxTest.testName }} &quot; 학습지</div>
                <template #footer>
                    <Button label="No" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="Yes" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
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
