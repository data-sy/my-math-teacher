<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import axios from 'axios';
import { useStore } from 'vuex';

const store = useStore();
const router = useRouter();
const api = useApi();

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
            const endpoint = 'tests/user';
            const response = await api.get(endpoint);
            listboxTests.value = response;
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    } else {
        console.log("사용자가 로그인하지 않았습니다. 학습지 목록을 건너뜁니다.");
    }
});
// 추가) 정오답에 따라 활성화 주고, 기록 이미 한 학습지는 분석결과 보러가기 or 재기록 버튼 (새 userTest로 다시 저장되도록)

// 학습지 미리보기
const testDetail = ref([]);
const testId = ref(null);
const userTestId = ref(null);
watch(listboxTest, async (newValue) => {
    testId.value = newValue.testId;
    userTestId.value = newValue.userTestId;
    try {
        const endpoint = `/tests/detail/${newValue.testId}`;
        const response = await api.get(endpoint);
        testDetail.value = response.map((item) => {
            return { ...item, answerCode: true };
        });
        // const modules = await import.meta.glob(`@/assets/images/items/diag/${testId.value}/*.jpg`);
        // const images = [];
        // for (const img in modules) {
        //     images.push(modules[img].default);
        // }
        // console.log(images);
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
});
// 답안 원문자 표현
const renderItemAnswer = (text) => {
    return text;
};
// 정오답 DB에 저장
const createRecord = async () => {
    if (isLoggedIn.value && userTestId.value !== null) {
        const answerCodeCreateRequestList = testDetail.value.map(({ itemId, answerCode }) => ({ itemId, answerCode: answerCode ? 1 : 0 }));
        const requestData = ref({
            userTestId: userTestId,
            answerCodeCreateRequestList: answerCodeCreateRequestList
        });
        try {
            await api.post('/record', requestData.value);
        } catch (err) {
            console.error(`POST ${endpoint} failed:`, err);
        }
    } else {
        console.log("사용자가 로그인하지 않았거나, userTestId가 없습니다. 기록을 건너뜁니다.");
    }
};
// AI 분석
const analysis = async () => {
    if (isLoggedIn.value && userTestId.value !== null) {
        const accessToken = localStorage.getItem('accessToken');
        if (!accessToken) {
                console.error('액세스 토큰이 없습니다.');
                return;
            }
        try {
            const headers = {
                "Authorization": `Bearer ${accessToken}`,
                "Content-Type": "application/json"
            };
            const response = await axios.post(`http://localhost:8000/ai/v1/ai/${userTestId.value}`, {}, { headers });
            console.log('응답 데이터:', response.data);
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    } else {
        console.log("userTestId가 없습니다. AI 분석을 건너뜁니다.");   
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
const confirmPopup2 = useConfirm();
const confirm2 = (event) => {
    confirmPopup2.require({
        target: event.target,
        message: '로그인 혹은 회원가입을 해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 기록할 수 있습니다.', life: 3000 });
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
// 많이 느려진다면 로딩 중 화면 띄우는 것 추가
// yes 버튼 클릭 시
const yesClick = async () => {
    closeConfirmation();
    await createRecord();
    await analysis();
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
                        <div class="text-900 font-medium text-xl mb-3">여기는 진단학습지의 정오답을 기록하는 곳이야.</div>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-600 font-medium mb-3"> 1. [학습지 목록]에서 기록할 학습지 선택</span>
                        <ul style="list-style-type: disc">
                            <li class="mb-2">"&#9312; 실력 점검하기"에서 다운로드했던 진단학습지 목록이 준비되어 있어</li>
                        </ul>
                        <span class="block text-600 font-medium mb-3"> 2. [정오답 기록하기]에서 o/x 선택하기 </span>
                        <ul style="list-style-type: disc">
                            <li class="mb-2">"정오답입력"의 ox버튼을 클릭하면 o/x를 선택할 수 있어</li>
                            <li class="mb-2">맞은 문제는 o, 틀린 문제는 x를 선택하면 돼</li>
                        </ul>
                        <span class="block text-600 font-medium"> 3. [기록하기] 버튼 누르기</span>
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
                <h5>정오답 기록하기</h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem' }" :pt="{ wrapper: { style: { 'border-right': '10px solid var(--surface-ground)' } }, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80' }">
                    <div v-if="!listboxTest"></div>
                    <div v-else-if="listboxTest.record"> {{listboxTest.testSchoolLevel}} - {{listboxTest.testGradeLevel}} 트루이면 안내문구와 함께 분석결과보기 링크</div>
                    <div v-else>
                        <DataTable :value="testDetail" rowGroupMode="subheader" groupRowsBy="representative.name" sortMode="single" sortField="representative.name" :sortOrder="1">
                            <Column field="testItemNumber" header="번호" style="min-width: 5em"></Column>
                            <Column field="itemAnswer" header="정답" style="min-width: 5em">
                                <template #body="rowData">
                                    <span v-html="renderItemAnswer(rowData.data.itemAnswer)"></span>
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
            <Button v-else @click="openConfirmation" label="기록하기" class="mr-2 mb-2" />
            <Dialog header="다음 정오답을 기록하시겠습니까?" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
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

<style>
.fit-image {
    max-width: 100%; /* 최대 너비를 부모 요소인 div의 크기에 맞게 조정합니다. */
    height: auto; /* 이미지의 가로세로 비율을 유지하면서 조정합니다. */
    display: block; /* 인라인 요소와의 공간을 없애기 위해 블록 요소로 변경합니다. */
}
</style>
