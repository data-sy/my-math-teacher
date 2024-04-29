<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/api.js';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import cytoscape from 'cytoscape';
import klay from 'cytoscape-klay';
import { useStore } from 'vuex';
import { VMarkdownView } from 'vue3-markdown'
import 'vue3-markdown/dist/style.css'

const store = useStore();
const dataToSend = history.state.dataToSend;
const receivedData = ref('')

cytoscape.use(klay);
const cyElement = ref(null);
let cy = null;

const router = useRouter()
const api = useApi();

const isLoggedIn = ref(false);
const listboxTest = ref(null);
const listboxTests = ref([]);
const resultList = ref([]);
const userTestId = ref(null);
const sortedResultList = ref([]);
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
            const endpoint = "/api/v1/tests/user/is-record" 
            const response = await api.get(endpoint);
            listboxTests.value = response;
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
        // [기록하기] 화면에서 넘어왔을 때는 해당 학습지 선택
        if (dataToSend) {
            receivedData.value = dataToSend
        }
        if (receivedData.value) {
            userTestId.value = receivedData.value.userTestId;
            if (userTestId.value !== null) {
                try {
                    const endpoint = `/api/v1/result/${userTestId.value}`;
                    const response = await api.get(endpoint);
                    resultList.value = response;
                    resultList.value.forEach(item => {
                        const representativeItem = resultList.value.find(e => e.testItemNumber === item.testItemNumber && e.toConceptDepth === 0);
                        if (representativeItem) {
                            item.representative = {
                                testItemNumber: item.testItemNumber,
                                conceptName: representativeItem.conceptName
                            };
                        }
                    });
                    const groupList = groupBy(resultList.value, 'testItemNumber');
                    sortedResultList.value = sortGroupByProbability(resultList.value);
                } catch (err) {
                    console.error('데이터 생성 중 에러 발생:', err);
                }
            } else {
                console.log("사용자가 로그인하지 않았거나, 학습지를 선택하지 않았습니다.");
            }
        }
    } else {
        console.log("사용자가 로그인하지 않았습니다. 학습지 목록을 건너뜁니다.");
    }
});
// 리팩토링) 기록 페이지에서 넘어왔다면 학습지 목록에 가상의 클릭 이벤트 추가하기 (목록에 선택된 학습지 보라색으로 체크되도록)
// 분석 결과
watch(listboxTest, async (newValue) => {
    if (newValue !== null ) {
        userTestId.value = newValue.userTestId;
        // isLoggedIn도 사실 넣어야 하지만 listboxTest가 isLoggedIn가 있어야만 생성되는 아이니까 패스
        if (userTestId.value !== null) {
            try {
                const endpoint = `/api/v1/result/${userTestId.value}`;
                const response = await api.get(endpoint);
                resultList.value = response;
                resultList.value.forEach(item => {
                    const representativeItem = resultList.value.find(e => e.testItemNumber === item.testItemNumber && e.toConceptDepth === 0);
                    if (representativeItem) {
                        item.representative = {
                            testItemNumber: item.testItemNumber,
                            conceptName: representativeItem.conceptName
                        };
                    }
                });
                sortedResultList.value = sortGroupByProbability(resultList.value);
            } catch (err) {
                console.error('데이터 생성 중 에러 발생:', err);
            }
        } else {
            console.log("사용자가 로그인하지 않았거나, 학습지를 선택하지 않았습니다.");
        }
    }
});
const calculateResultTotal = (testItemNumber) => {
    let total = 0;
    if (resultList.value) {
        for (let result of resultList.value) {
            if (result.testItemNumber === testItemNumber) {
                total++;
            }
        }
    }
    return total;
};
// testItemNumber를 기준으로 그룹화 한 후 확률 값 크기순으로 정렬
const sortGroupByProbability = (array) => {
    // 그룹화
    const grouped =  array.reduce((acc, obj) => {
        const keyValue = obj['testItemNumber'];
        if (!acc[keyValue]) {
            acc[keyValue] = [];
        }
        acc[keyValue].push(obj);
        return acc;
    }, {});
    // 그룹별 정렬
    for (const group in grouped) {
        grouped[group].sort((a, b) => a.probabilityPercent - b.probabilityPercent);
        // 시급도 할당
        setPriority(grouped[group]);
    }
    // 그룹화 해제
    const flattenedArray = Object.values(grouped).reduce((acc, group) => {
        acc.push(...group);
        return acc;
    }, []);
    return flattenedArray;
};
// priority에 시급도를 할당하는 함수
const setPriority = (data) => {
    const totalItems = data.length;
    data.forEach((item, index) => {
        if (index < totalItems / 3) {
            item.priority = '상';
        } else if (index < (totalItems * 2) / 3) {
            item.priority = '중';
        } else {
            item.priority = '하';
        }
    });
};
// 각 priority에 해당하는 태그 매칭하기
const getPriority = (status) => {
    switch (status) {
        case '상':
            return 'danger'; // 빨강

        case '중':
            return 'warning'; // 주황

        case 'new':
            return 'success'; // 기본
            // return 'info'; // 기본
    }
};

// 학습지 목록 누르지 않고 [맞춤 학습지 출제]버튼을 누르면, 학습지를 먼저 골라달라고 안내
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
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '학습지를 선택하면 그에 따른 맞춤 학습지를 출제할 수 있습니다.', life: 3000 });
        },
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
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 결과를 볼 수 있습니다.', life: 3000 });
        },
    });
};
// '홈으로' 버튼
const goToHome = () => {
  try {
    router.push({ path: '/' }); 
  } catch (error) {
    console.error('에러 발생:', error);
  }
};
// '맞춤학습지 출제' 버튼 : 데이터 가지고 화면이동
const goToNextPage = async () => {
    const data = {
        userTestId: userTestId.value,
    }
    router.push({
        name: 'personal',
        state: {dataToSend: data}
    });
};
</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 text-center">
            <div v-if="!isLoggedIn" class="text-orange-500 font-medium text-3xl">로그인이 필요한 페이지 입니다.</div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card"> 
                <h5> 정오답 기록한 학습지 목록 </h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName"/>
            </div>
        </div>
        <div class="col-12 xl:col-9">
             <!-- {{ resultList }} -->
            <div class="card">
                <h5> 분석 결과 </h5>
                <!-- <ScrollPanel :style="{ width: '100%', height: '35rem'}" :pt="{wrapper: {style: {'border-right': '10px solid var(--surface-ground)'}}, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80'}">  -->
                    <DataTable :value="resultList" rowGroupMode="subheader" groupRowsBy="representative.testItemNumber" sortMode="single"
                            sortField="representative.testItemNumber" :sortOrder="1" scrollable scrollHeight="30rem" tableStyle="min-width: 50rem">
                        <Column field="representative.testItemNumber" header="Representative"></Column>
                        <Column field="priority" header="시급도">
                            <template #body="slotProps">
                                <Tag :value="slotProps.data.priority" :severity="getPriority(slotProps.data.priority)" :rounded="true" />
                            </template>
                        </Column>
                        <Column field="toConceptDepth" header="선수지식 깊이" style="min-width: 20px"></Column>
                        <Column field="conceptName" header="개념" style="min-width: 200px"></Column>
                        <Column field="level" header="학교-학년-학기" style="min-width: 120px"></Column>
                        <Column field="chapter" header="단원" style="min-width: 300px"></Column>
                        <template #groupheader="slotProps">
                            <div class="flex align-items-center gap-2 text-xl text-primary">
                                <span class="font-bold mx-2"> [문항 {{ slotProps.data.testItemNumber }}번] </span>
                                <span>{{ slotProps.data.representative.conceptName }}</span>
                            </div>
                        </template>
                        <template #groupfooter="slotProps">
                            <div class="flex justify-content-end font-bold w-full"> 전체 개수 : {{ calculateResultTotal(slotProps.data.testItemNumber) }}</div>
                        </template>

                    </DataTable>
                <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
            </div>
            <div class="card">
                <h5> 선수지식 TREE </h5>
            </div>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2 mb-5">
            <Button @click="goToHome" label="홈으로" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8"></div>
        <!-- <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToNextPage" label="맞춤 학습지 출제"  class="mr-2 mb-2"></Button>
        </div> -->
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <Button v-if="!isLoggedIn" ref="popup" @click="confirm2($event)" label="로그인을 해주세요." icon="pi pi-download" class="mr-2 mb-2"></Button>
            <Button v-else-if="userTestId == null" ref="popup" @click="confirm($event)" label="학습지를 선택하세요." class="mr-2 mb-2"></Button>
            <Button v-else @click="goToNextPage" label="맞춤 학습지 출제"  class="mr-2 mb-2"></Button>
        </div>
    </div>
</template>

<style scoped>
.clickable {
  cursor: pointer;
  text-decoration: underline;
  color: blue;
}
</style>
