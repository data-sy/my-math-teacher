<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import { useConceptGraph } from '@/composables/useConceptGraph.js';
import levelDic from '@/assets/data/level.json';
import { VMarkdownView } from 'vue3-markdown';
import 'vue3-markdown/dist/style.css';

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
watch(listboxConcept, async (newValue) => {
    if (newValue !== null) {
        conceptId.value = newValue.conceptId;
        // console.log(conceptId.value);
        // conceptId에 따른 conceptDetail
        try {
            const endpoint = `/api/v1/concepts/${conceptId.value}`;
            const response = await api.get(endpoint);
            response.conceptDescription = response.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne');
            conceptDetail.value = response;
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
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
// 홈으로
const goToHome = () => {
    try {
        router.push({ path: '/' });
    } catch (err) {
        console.error('에러 발생:', err);
    }
};

// cytoscape (렌더링 레이어는 useConceptGraph 컴포저블이 소유)
const cyElement = ref(null);
const { initGraph, destroy: destroyGraph, GRADE_COLORS } = useConceptGraph();

// 선수지식 데이터
const uniqueConceptIds = new Set();
const knowledgeSpace = ref([]);
const clickedNodeId = ref('');
const nodesResponse = ref(null);
const showKnowledgeSpace = async () => {
    clearCy();
    try {
        const nodesEndpoint = `/api/v1/concepts/nodes/${conceptDetail.value.conceptId}`;
        nodesResponse.value = await api.get(nodesEndpoint);
        const edgesEndpoint = `/api/v1/concepts/edges/${conceptDetail.value.conceptId}`;
        const edgesResponse = await api.get(edgesEndpoint);
        // nodes -> knowledgeSpace의 data
        nodesResponse.value.forEach((node) => {
            uniqueConceptIds.add(node.conceptId);
        });
        // 중복이 제거된 conceptId를 가지고 knowledgeSpace에 데이터 추가
        uniqueConceptIds.forEach((uniqueConceptId) => {
            const filteredNode = nodesResponse.value.find((node) => node.conceptId === uniqueConceptId);
            if (filteredNode) {
                knowledgeSpace.value.push({
                    data: {
                        id: filteredNode.conceptId.toString(),
                        label: filteredNode.conceptName,
                        conceptGradeLevel: filteredNode.conceptGradeLevel
                    }
                });
            }
        });
        // edges -> knowledgeSpace의 data
        edgesResponse.forEach((edge) => {
            // edge의 source가 nodes의 conceptId에 있는지 확인 (나중에 미리 백단에서 걸러오는 방법으로 리팩토링)
            const sourceExists = nodesResponse.value.some((node) => {
                return node.conceptId === parseInt(edge.data.source);
            });
            // target이 nodes 안에 있을 경우만 추가
            if (sourceExists) {
                knowledgeSpace.value.push(edge);
            }
        });
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
    initGraph(cyElement.value, knowledgeSpace.value, {
        onTapNode: (id) => {
            clickedNodeId.value = id;
        }
    });
    // 아래로 스크롤
    const selectedNodeElement = document.getElementById('scroll-tree');
    if (selectedNodeElement) {
        selectedNodeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
};

// 컴포넌트 파기 시 Cytoscape 인스턴스 파기
onBeforeUnmount(() => {
    clearCy();
});

// 노드 클릭 시 해당 노드의 데이터 화면에 보여주기
const selectedNode = ref('');
watch(clickedNodeId, (newValue) => {
    const selectedNodeId = parseInt(newValue);
    if (newValue && nodesResponse.value) {
        selectedNode.value = nodesResponse.value.find((node) => node.conceptId === selectedNodeId);
        selectedNode.value.conceptDescription = selectedNode.value.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne');
    }
    const selectedNodeElement = document.getElementById('scroll-node');
    if (selectedNodeElement) {
        selectedNodeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
});
// ? 아이콘에 설명서 마우스오버 & 마우스아웃
const op = ref(null);
const showSpec = (event) => {
    op.value.toggle(event);
};
const hideSpec = () => {
    op.value.toggle(null);
};
// Cytoscape 인스턴스 파기
const clearCy = () => {
    knowledgeSpace.value = [];
    destroyGraph();
};
</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 md:col-6 xl:col-3">
            <div class="card">
                <h5>학교급</h5>
                <SelectButton v-model="selectButtonLevel" :options="selectButtonLevels" optionLabel="name" />
            </div>
            <div class="card">
                <h5>학년</h5>
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

        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToHome" label="홈으로" icon="pi pi-home" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8"></div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <Button v-if="conceptId == null" ref="popup" @click="confirm($event)" label="개념을 선택해주세요." class="mr-2 mb-2"></Button>
            <Button v-else @click="showKnowledgeSpace" label="선수지식 확인" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-12">
            <div class="card" id="scroll-tree">
                <div class="flex align-items-center mb-5">
                    <div class="text-2xl font-semibold mx-2">선수지식 트리</div>
                    <div class="mx-3">점을 클릭해보세요. 개념 상세보기를 할 수 있습니다.</div>
                    <div><i class="pi pi-question-circle font-semibold mx-2" @mouseover="showSpec" @mouseout="hideSpec" style="font-size: 1.5rem"></i></div>
                </div>
                <OverlayPanel ref="op" appendTo="body">
                    <li class="text-600 font-medium mb-3">스크롤 : 화면 확대/축소</li>
                    <li class="text-600 font-medium mb-3">점 클릭 & 드래그 : 점 이동</li>
                    <li class="text-red-700 font-bold">점 클릭 : [선수지식 상세보기]</li>
                </OverlayPanel>
                <div>
                    <div ref="cyElement" style="height: 400px; width: 100%"></div>
                </div>
                <ul style="list-style-type: disc">
                    <li class="text-600 font-medium mb-3">
                        초등학교 : 초1,2 <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['초1'], fontSize: '1.5rem' }"></i> 초3,4 <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['초3'], fontSize: '1.5rem' }"></i> 초5,6
                        <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['초5'], fontSize: '1.5rem' }"></i>
                    </li>
                    <li class="text-600 font-medium mb-3">
                        중학교 : 중1 <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['중1'], fontSize: '1.5rem' }"></i> 중2 <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['중2'], fontSize: '1.5rem' }"></i> 중3
                        <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['중3'], fontSize: '1.5rem' }"></i>
                    </li>
                    <li class="text-600 font-medium">
                        고등학교 : 수학(상/하) <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['수학'], fontSize: '1.5rem' }"></i> 수&#8544;,수&#8545;
                        <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['수1'], fontSize: '1.5rem' }"></i> 미적,기하,확통
                        <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['미적'], fontSize: '1.5rem' }"></i>
                    </li>
                </ul>
            </div>
        </div>
        <div class="col-12 lg:col-6 mb-6">
            <div class="card" id="scroll-node">
                <h5>선수지식 상세보기</h5>
                <div class="surface-section" v-if="selectedNode">
                    <div>
                        <VMarkdownView :content="selectedNode.conceptName" class="font-medium text-4xl text-900 mb-3"></VMarkdownView>
                    </div>
                    <div class="text-500 mb-5"></div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">학교-학년-학기</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode.conceptSchoolLevel }}-{{ selectedNode.conceptGradeLevel }}-{{ selectedNode.conceptSemester }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">대-중-소단원</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode.conceptChapterMain }}-{{ selectedNode.conceptChapterSub }}-{{ selectedNode.conceptChapterName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode.conceptAchievementName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-primary-500 w-6 md:w-3 font-xl font-bold">개념설명</div>
                            <div class="text-900 font-medium w-full md:w-9 md:flex-order-0 flex-order-1">
                                <VMarkdownView :content="selectedNode.conceptDescription"></VMarkdownView>
                            </div>
                        </li>
                    </ul>
                </div>
                <div class="surface-section" v-else>
                    <div class="font-medium text-3xl text-900 mb-3 text-blue-500">개념을 선택해주세요</div>
                    <div class="text-500 mb-5"></div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">학교-학년-학기</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1"></div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">대-중-소단원</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1"></div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1"></div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">개념설명</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1"></div>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
        <div class="col-12 lg:col-6 mb-6">
            <div class="card">
                <h5>개념 상세보기</h5>
                <div class="surface-section" v-if="conceptDetail">
                    <div>
                        <VMarkdownView :content="conceptDetail.conceptName" class="font-medium text-4xl text-900 mb-3"></VMarkdownView>
                    </div>
                    <div class="text-500 mb-5"></div>
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
            </div>
        </div>
    </div>
</template>
