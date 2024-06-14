<script setup>
import { ref, watch, onMounted, computed } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import { useHtmlToPdf } from '@/composables/htmlToPdf';
import TitleService from '@/service/TitleService';
import levelDic from '@/assets/data/level.json';
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
// schoolLevel
const selectButtonLevel = ref(null);
const selectButtonLevels = ref([{ name: '초등' }, { name: '중등' }, { name: '고등' }]);
const schoolLevel = ref('');
// gradeLevel
const listboxLevel = ref(null);
const listboxLevels = ref([]);
const listboxTestsAll = ref(null);
watch(selectButtonLevel, async (newValue) => {
    if (newValue !== null) {
        schoolLevel.value = newValue.name;
        if (newValue.name === '초등') {
            listboxLevels.value = levelDic['초등'];
        } else if (newValue.name === '중등') {
            listboxLevels.value = levelDic['중등'];
        } else if (newValue.name === '고등') {
            listboxLevels.value = levelDic['고등'];
        }
        try {
            const endpoint = `/api/v1/tests/school-level/${newValue.name}`;
            const response = await api.get(endpoint);
            listboxTestsAll.value = response;
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    }
});
const listboxTest = ref(null);
const listboxTests = ref([]);
const grade = ref(null);
const semester = ref(null);
// 학습지 목록
watch(listboxLevel, (newValue) => {
    if (newValue !== null) {
        grade.value = newValue.grade;
        semester.value = newValue.semester;
        if (listboxTestsAll.value) {
            const filteredTests = listboxTestsAll.value.filter((test) => {
                return test.testGradeLevel === grade.value && test.testSemester === semester.value;
            });
            listboxTests.value = filteredTests;
        }
    }
});
// 학습지 미리보기
const testDetail = ref([]);
const testId = ref(null);
const isImageExist = ref(false);
const testName = ref('');
watch(listboxTest, async (newValue) => {
    if (newValue !== null) {
        testId.value = newValue.testId;
        testName.value = newValue.testName;
        if (testId.value >= 491 && testId.value <= 495) {
            isImageExist.value = true;
        } else {
            isImageExist.value = false;
        }
        try {
            const endpoint = `/api/v1/tests/detail/${testId.value}`;
            const response = await api.get(endpoint);
            if (testId.value < 491 || testId.value > 495) {
                testDetail.value = response.map((item) => {
                    return {
                        ...item,
                        itemImagePath: "/images/items/empty001.jpg"
                    };
                });
            } else {
                testDetail.value = response;
            }
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    }
});
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
// 유저-학습지 DB에 저장
const createDiagTest = async () => {
    if (isLoggedIn.value) {
        try {
            const endpoint = `/api/v1/tests/${testId.value}`;
            await api.post(endpoint);
        } catch (err) {
            console.error(`POST ${endpoint} failed:`, err);
        }
    } else {
        console.log('사용자가 로그인하지 않았습니다. 테스트 생성을 건너뜁니다.');
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
        }
    });
};
// 로그인 하지 않고 [다운로드] 버튼을 누르면, 회원가입이나 로그인을 먼저 해달라고 안내
const confirmPopup2 = useConfirm();
const confirm2 = (event) => {
    confirmPopup2.require({
        target: event.target,
        message: '로그인 혹은 회원가입을 해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 학습지를 다운로드할 수 있습니다.', life: 3000 });
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
        <div class="col-12 text-center">
            <div v-if="!isLoggedIn" class="text-orange-500 font-medium text-3xl">로그인이 필요한 페이지 입니다.</div>
        </div>
        <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between">
                    <div>
                        <span class="text-red-500 text-lg font-medium"> 저작권 제약으로 실제 문제가 제공되지는 않습니다. </span>
                        <span class="mt-2 block font-medium"> - 준비된 샘플 학습지 : 고등 -> 수학(상) -> 복소수와 이차방정식(1)~(5) </span>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5>School Level</h5>
                <SelectButton v-model="selectButtonLevel" :options="selectButtonLevels" optionLabel="name" />
            </div>
            <div class="card">
                <h5>Grade Level</h5>
                <Listbox v-model="listboxLevel" :options="listboxLevels" optionLabel="name" />
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5>학습지 목록</h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem' }" :pt="{ wrapper: { style: { 'border-right': '10px solid var(--surface-ground)' } }, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80' }">
                    <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" :filter="true" />
                    <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
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
                                            <span class="text-sm sm:text-lg md:text-xl lg:text-2xl xl:text-xl font-medium text-900 mx-2"> {{ schoolLevel }} - {{ grade }} - {{ semester }} </span>
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
            <Button v-if="testId == null" ref="popup" @click="confirm($event)" label="학습지를 선택하세요." icon="pi pi-download" class="mr-2 mb-2"></Button>
            <Button v-else-if="!isLoggedIn" ref="popup" @click="confirm2($event)" label="로그인을 해주세요." icon="pi pi-download" class="mr-2 mb-2"></Button>
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
