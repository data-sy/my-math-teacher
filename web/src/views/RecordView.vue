<script setup>
import { onMounted, ref, watch, computed } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import TitleService from '@/service/TitleService';
// import axios from 'axios';
import { VMarkdownView } from 'vue3-markdown';
import 'vue3-markdown/dist/style.css';

const store = useStore();
const router = useRouter();
const api = useApi();

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
onMounted(async () => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null;
    watch(
        () => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    );
    if (isLoggedIn.value) {
        try {
            const endpoint = '/api/v1/users';
            const response = await api.get(endpoint);
            userDetail.value = response;
            userGrade.value = TitleService.calculateGrade(userDetail.value.userBirthdate);
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    } else {
        console.log('사용자가 로그인하지 않았습니다. 유저 정보를 건너뜁니다.');
    }
});
// 학습지 목록
const listboxTest = ref(null);
const listboxTests = ref([]);
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
            const endpoint = '/api/v1/tests/user';
            const response = await api.get(endpoint);
            listboxTests.value = response;
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    // 로그인 안 했을 때는 샘플 학습지
    } else {
        console.log('사용자가 로그인하지 않았습니다. 샘플 학습지 목록을 제공합니다.');
        try {
            const endpoint = '/api/v1/tests/sample';
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
// 추가) 정오답에 따라 활성화 주고, 기록 이미 한 학습지는 분석결과 보러가기 or 재기록 버튼 (새 userTest로 다시 저장되도록)

// 학습지 미리보기
const testDetail = ref([]);
const testId = ref(null);
const isImageExist = ref(false);
const testName = ref('');
const testDate = ref('');
const userTestId = ref(null);
const schoolLevel = ref('');
const grade = ref(null);
const semester = ref(null);
const isRecord = ref(false);
watch(listboxTest, async (newValue) => {
    isImageExist.value = false;
    if (newValue !== null) {
        testId.value = newValue.testId;
        testName.value = newValue.testName;
        testDate.value = newValue.testDate;
        schoolLevel.value = newValue.testSchoolLevel;
        grade.value = newValue.testGradeLevel;
        semester.value = newValue.testSemester;
        isRecord.value = newValue.record;
        if (testId.value >= 491 && testId.value <= 495) {
            isImageExist.value = true;
        } else {
            isImageExist.value = false;
        }
        userTestId.value = newValue.userTestId;
        try {
            const endpoint = `/api/v1/tests/detail/${newValue.testId}`;
            const response = await api.get(endpoint);
            if (testId.value < 491 || testId.value > 495) {
                testDetail.value = response.map((item) => {
                    return {
                        ...item,
                        itemImagePath: '/images/items/empty001.jpg',
                        answerCode: true
                    };
                });
            } else {
                testDetail.value = response.map((item) => {
                    return { ...item, answerCode: true };
                });
            }
            // const modules = await import.meta.glob(`@/assets/images/items/diag/${testId.value}/*.jpg`);
            // const images = [];
            // for (const img in modules) {
            //     images.push(modules[img].default);
            // }
            // console.log(images);
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
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
// 답안 원문자 표현
const renderItemAnswer = (text) => {
    return text;
};
const isLatex = (answer) => {
    return !answer.includes('&#');
};
// deprecated
// // 정오답 DB에 저장
// const createRecord = async () => {
//     if (isLoggedIn.value && userTestId.value !== null) {
//         const answerCodeCreateRequestList = testDetail.value.map(({ itemId, answerCode }) => ({ itemId, answerCode: answerCode ? 1 : 0 }));
//         const requestData = ref({
//             userTestId: userTestId,
//             answerCodeCreateRequestList: answerCodeCreateRequestList
//         });
//         try {
//             await api.post('/api/v1/record', requestData.value);
//         } catch (err) {
//             console.error(`POST ${endpoint} failed:`, err);
//         }
//     } else {
//         console.log('사용자가 로그인하지 않았거나, userTestId가 없습니다. 기록을 건너뜁니다.');
//     }
// };
// // AI 분석
// const analysis = async () => {
//     if (isLoggedIn.value && userTestId.value !== null) {
//         const accessToken = localStorage.getItem('accessToken');
//         if (!accessToken) {
//             console.error('액세스 토큰이 없습니다.');
//             return;
//         }
//         try {
//             const headers = {
//                 Authorization: `Bearer ${accessToken}`,
//                 'Content-Type': 'application/json'
//             };
//             const response = await axios.post(`http://localhost:8000/ai/v1/ai/${userTestId.value}`, {}, { headers });
//             // console.log('응답 데이터:', response.data);
//         } catch (err) {
//             console.error('데이터 생성 중 에러 발생:', err);
//         }
//     } else {
//         console.log('userTestId가 없습니다. AI 분석을 건너뜁니다.');
//     }
// };
// AI 분석
const predict = async () => {
    if (isLoggedIn.value && userTestId.value !== null) {
        const answerCodeCreateRequestList = testDetail.value.map(({ itemId, answerCode }) => ({ itemId, answerCode: answerCode ? 1 : 0 }));
        const requestData = ref({
            userTestId: userTestId,
            answerCodeCreateRequestList: answerCodeCreateRequestList
        });
        try {
            await api.post('api/v1/ai', requestData.value);
        } catch (err) {
            console.error(`POST ${endpoint} failed:`, err);
        }
    } else {
        console.log("사용자가 로그인하지 않았거나, userTestId가 없습니다. 기록을 건너뜁니다.");
    }
};
// 학습지를 누르지 않고 [기록하기]버튼을 누르면, 학습지 목록에서 학습지를 먼저 골라달라고 안내
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
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '학습지를 선택하면 기록할 수 있습니다.', life: 3000 });
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
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 기록할 수 있습니다.', life: 3000 });
        }
    });
};
// 이미 기록한 학습지에 대해서는, 이미 기록한 학습지라고 안내
const confirm3 = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '이미 기록한 학습지 입니다.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '3. 분석 결과보기로 확인하세요.', life: 3000 });
        }
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
// 많이 느려진다면 로딩 중 화면 띄우는 것 추가
// yes 버튼 클릭 시
const yesClick = async () => {
    closeConfirmation();
    // await createRecord();
    // await analysis();
    await predict();
    goToResultPage();
};
// [여기] 클릭 시 : userTestId 가지고 result로 이동
const goToResultPage = async () => {
    const data = {
        userTestId: userTestId.value
    };
    router.push({
        name: 'result',
        state: { dataToSend: data }
    });
};
</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 text-center">
            <div v-if="!isLoggedIn" class="text-orange-500 font-medium text-3xl">로그인이 필요한 페이지 입니다.</div>
        </div>
        <div class="col-12 sm:col-6 xl:col-3">
            <div class="card">
                <h5>다운로드한 학습지 목록</h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" />
            </div>
        </div>
        <div class="col-12 sm:col-6 xl:col-3">
            <div class="card">
                <h5>정오답 기록하기</h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem' }" :pt="{ wrapper: { style: { 'border-right': '10px solid var(--surface-ground)' } }, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80' }">
                    <div v-if="!listboxTest"></div>
                    <div v-else-if="listboxTest.record">
                        <div class="mx-2 my-5 text-2xl text-bold text-pink-500">
                            <div>정오답이 이미 기록된 학습지입니다.</div>
                            <div>
                                AI 분석 결과가 궁금하면
                                <span @click="goToResultPage" class="text-blue-500 cursor-pointer"> [여기] </span>
                                를 클릭해주세요.
                            </div>
                        </div>
                    </div>
                    <div v-else>
                        <DataTable :value="testDetail" rowGroupMode="subheader" groupRowsBy="representative.name" sortMode="single" sortField="representative.name" :sortOrder="1">
                            <Column field="testItemNumber" header="번호" style="min-width: 5em"></Column>
                            <Column field="itemAnswer" header="정답" style="min-width: 5em">
                                <template #body="rowData">
                                    <span v-if="isImageExist">
                                        <VMarkdownView v-if="isLatex(rowData.data.itemAnswer)" :content="rowData.data.itemAnswer"></VMarkdownView>
                                        <span v-else v-html="renderItemAnswer(rowData.data.itemAnswer)"></span>
                                    </span>
                                </template>
                            </Column>
                            <Column field="answerCode" header="정오답입력" style="min-width: 5em">
                                <template #body="rowData">
                                    <ToggleButton v-model="rowData.data.answerCode" onLabel="o" offLabel="x" :style="{ width: '3.3em' }" />
                                </template>
                            </Column>
                        </DataTable>
                    </div>
                    <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                <!--'기록하기'에서는 정답 삭제-->
                <h5>학습지 미리보기</h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem' }" :pt="{ wrapper: { style: { 'border-right': '10px solid var(--surface-ground)' } }, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80' }">
                    <div id="testImage" ref="pdfAreaRef">
                        <div v-if="isImageExist" class="grid mx-2 my-4">
                            <div class="testItemBox col-12" style="aspect-ratio: 5/1">
                                <div class="grid">
                                    <div class="col-12 mx-3 mt-3 logo">
                                        <img :src="logoUrl" alt="logo" />
                                        <span class="text-lg sm:text-2xl md:text-3xl lg:text-4xl xl:text-3xl"> MMT</span>
                                        <span class="text-xs sm:text-base md:text-lg lg:text-xl xl:text-lg ml-auto px-5"> 문의 : contact.mmt.2024@gmail.com </span>
                                    </div>
                                    <div class="col-12">
                                        <div class="flex justify-content-between">
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl font-medium text-900 mx-2"> {{ schoolLevel }} - {{ grade }} - {{ semester }} </span>
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl mx-2">{{ testDate }}</span>
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
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl font-medium text-900 mx-2"> {{ schoolLevel }} - {{ grade }} - {{ semester }} </span>
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl mx-2">{{ testDate }}</span>
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
            <Button v-else-if="isRecord" ref="popup" @click="confirm3($event)" label="이미 기록한 학습지입니다." class="mr-2 mb-2"></Button>
            <Button v-else @click="openConfirmation" label="기록하기" class="mr-2 mb-2" />
            <Dialog header="다음 정오답을 기록하시겠습니까?" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="text-500 font-semibold px-3 mb-5">기록 성공 시, HOME으로 이동합니다.</div>
                <div v-for="(item, index) in testDetail" :key="index" class="text-500 font-semibold px-3 py-1">
                    <div>{{ item.testItemNumber }}번 : {{ item.answerCode ? 'o' : 'x' }}</div>
                </div>
                <template #footer>
                    <Button label="No" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="Yes" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
                </template>
            </Dialog>
        </div>
    </div>
</template>

<style scoped>
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
