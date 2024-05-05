<script setup>
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { VMarkdownView } from 'vue3-markdown';
import 'vue3-markdown/dist/style.css';
import levelDic from '@/assets/data/level.json';

const router = useRouter();
const api = useApi();

// schoolLevel
const selectButtonLevel = ref(null);
const selectButtonLevels = ref([{ name: '초등' }, { name: '중등' }, { name: '고등' }]);
const treeValue = ref(null);
// gradeLevel
const listboxLevel = ref(null);
const listboxLevels = ref([]);
watch(selectButtonLevel, (newValue, oldValue) => {
    if (newValue !== null) {
        if (newValue.name === '초등') {
            listboxLevels.value = levelDic['초등'];
        } else if (newValue.name === '중등') {
            listboxLevels.value = levelDic['중등'];
        } else if (newValue.name === '고등') {
            listboxLevels.value = levelDic['고등'];
        }
        if (oldValue !== null && newValue.name !== oldValue.name) {
            treeValue.value = null;
        }
    }
});
// chapeterLevel
watch(listboxLevel, async (newValue) => {
    if (newValue !== null) {
        const grade = newValue.grade;
        const semester = newValue.semester;
        try {
            const endpoint = `/api/v1/chapters?grade=${grade}&semester=${semester}`;
            const response = await api.get(endpoint);
            if (response[0]['label'] === '') {
                treeValue.value = response[0]['children'];
            } else {
                treeValue.value = response;
            }
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    }
});
// 개념 목록
const selectedTreeValue = ref(null);
const expandedKeys = ref({});
const listboxConcept = ref(null);
const listboxConcepts = ref([]);
watch(selectedTreeValue, async (newValue) => {
    const key = Object.keys(newValue)[0];
    // 대단원, 중단원일 때 : 클릭 시 expandNode & collapse 토글
    if (!expandedKeys.value[key]) {
        // 확장되지 않은 상태면 확장
        expandedKeys.value[key] = true;
    } else {
        // 이미 확장된 상태면 축소
        delete expandedKeys.value[key];
    }
    // 소단원일 때 : key가 정수 & 클릭 시 개념 목록 api
    const chapterId = parseInt(key);
    if (!isNaN(chapterId)) {
        try {
            const endpoint = `/api/v1/concepts?chapterId=${chapterId}`;
            const response = await api.get(endpoint);
            listboxConcepts.value = response;
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    }
});
// 개념 상세보기
const conceptId = ref(null);
const conceptDetail = ref(null);
const testtest = ref(null);
watch(listboxConcept, (newValue) => {
    if (newValue !== null) {
        conceptDetail.value = newValue;
        conceptId.value = conceptDetail.value.conceptId;
        // console.log(conceptId.value);
        conceptDetail.value.conceptDescription = conceptDetail.value.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne');
    }
});

// 개념을 누르지 않고 [선수지식 확인]버튼을 누르면, 개념 목록에서 개념을 먼저 골라달라고 안내
const popup = ref(null);
const toast = useToast();
const confirmPopup = useConfirm();
const confirm = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '개념 목록에서 개념을 선택해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '개념을 선택하면 선수지식을 확인할 수 있습니다.', life: 3000 });
        }
    });
};
// '이전' 버튼 (홈으로)
const goToHome = () => {
    try {
        router.push({ path: '/' });
    } catch (err) {
        console.error('에러 발생:', err);
    }
};
// '다음' 버튼 : api & 화면이동
const goToNextPage = async () => {
    try {
        const nodesEndpoint = `/api/v1/concepts/nodes/${conceptDetail.value.conceptId}`;
        const nodesResponse = await api.get(nodesEndpoint);
        const edgesEndpoint = `/api/v1/concepts/edges/${conceptDetail.value.conceptId}`;
        const edgesResponse = await api.get(edgesEndpoint);
        const data = {
            conceptId: conceptId.value,
            nodes: nodesResponse,
            edges: edgesResponse
        };
        router.push({
            name: 'concepttree',
            state: { dataToSend: data }
        });
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
};
</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 md:col-6 xl:col-3">
            <div class="card">
                <h5>School Level</h5>
                <SelectButton v-model="selectButtonLevel" :options="selectButtonLevels" optionLabel="name" />
            </div>
            <div class="card">
                <h5>Grade Level</h5>
                <Listbox v-model="listboxLevel" :options="listboxLevels" optionLabel="name" />
            </div>
        </div>
        <div class="col-12 md:col-6 xl:col-3">
            <div class="card">
                <h5>대단원-중단원-소단원</h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem' }" :pt="{ wrapper: { style: { 'border-right': '10px solid var(--surface-ground)' } }, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80' }">
                    <Tree :value="treeValue" :filter="true" filterMode="lenient" selectionMode="single" v-model:selectionKeys="selectedTreeValue" v-model:expandedKeys="expandedKeys" loadingMode="icon"></Tree>
                    <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                <h5>개념 목록</h5>
                <Listbox v-model="listboxConcept" :options="listboxConcepts" optionLabel="conceptName" :filter="true" />
            </div>
            <div class="card">
                <div class="surface-section" v-if="conceptDetail">
                    <div>
                        <VMarkdownView :content="conceptDetail.conceptName" class="font-medium text-4xl text-900 mb-3"></VMarkdownView>
                    </div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">학교-학년-학기</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ conceptDetail.conceptSchoolLevel }}-{{ conceptDetail.conceptGradeLevel }}-{{ conceptDetail.conceptSemester }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">대-중-소단원</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ conceptDetail.conceptChapterMain }}-{{ conceptDetail.conceptChapterSub }}-{{ conceptDetail.conceptChapterName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ conceptDetail.conceptAchievementName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-primary-500 w-6 md:w-3 font-xl font-bold">개념설명</div>
                            <div class="text-900 font-medium w-full md:w-9 md:flex-order-0 flex-order-1">
                                <VMarkdownView :content="conceptDetail.conceptDescription"></VMarkdownView>
                            </div>
                        </li>
                    </ul>
                </div>
                <div class="surface-section" v-else>
                    <div class="font-medium text-3xl text-900 mb-3 text-blue-500">개념 목록에서 개념을 선택해주세요</div>
                    <div class="text-500 mb-5"></div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 font-medium">학교-학년-학기</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1"></div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">대-중-소단원</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1"></div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1"></div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">개념설명</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1"></div>
                        </li>
                    </ul>
                </div>
            </div>
        </div>

        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2 mb-5">
            <Button @click="goToHome" label="이전" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8"></div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <Button v-if="conceptId == null" ref="popup" @click="confirm($event)" label="개념을 선택해주세요." class="mr-2 mb-2"></Button>
            <Button v-else @click="goToNextPage" label="선수지식 확인" class="mr-2 mb-2"></Button>
        </div>
    </div>
</template>
