<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import cytoscape from 'cytoscape';
import klay from 'cytoscape-klay';
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
            response.conceptDescription = response.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne')
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

// cytoscape
cytoscape.use(klay);
const cyElement = ref(null);
let cy = null;

// 크기 기본값
const nodeSize = 7;
const fontSize = 7;
const edgeWidth = '2px';
const arrowScale = 0.8;
// 색상 기본값
const dimColor = '#dfe4ea';
const edgeColor = '#ced6e0';
const nodeColor = '#57606f'; // 글씨색
// activate 시 크기
const nodeActiveSize = 15;
const fontActiveSize = 11;
const edgeActiveWidth = '4px';
const arrowActiveScale = 1.2;
// activate 시 색상
const nodeActiveColor = '#6466f1'; // 선택한 노드
const fromColor = '#ff6348'; // 후수지식
const toColor = '#1e90ff'; // 선수지식

const setDimStyle = (target_cy, style) => {
    target_cy.nodes().forEach((target) => {
        target.style(style);
    });
    target_cy.edges().forEach((target) => {
        target.style(style);
    });
};
const setFocus = (target_element, fromColor, toColor, edgeWidth, arrowScale) => {
    if (!target_element) {
        console.error('Invalid target element.');
        return;
    }
    target_element.style('background-color', nodeActiveColor);
    target_element.style('width', Math.max(parseFloat(target_element.style('width')), nodeActiveSize));
    target_element.style('height', Math.max(parseFloat(target_element.style('height')), nodeActiveSize));
    target_element.style('font-size', Math.max(parseFloat(target_element.style('font-size')), fontActiveSize));
    target_element.style('color', nodeColor);
    target_element.successors().each((e) => {
        if (e.isEdge()) {
            e.style('width', edgeWidth);
            e.style('arrow-scale', arrowScale);
        }
        e.style('color', nodeColor);
        e.style('background-color', fromColor);
        e.style('line-color', fromColor);
        e.style('target-arrow-color', fromColor);
        e.style('opacity', 0.5);
    });
    target_element.predecessors().each((e) => {
        if (e.isEdge()) {
            e.style('width', edgeWidth);
            e.style('arrow-scale', arrowScale);
        }
        e.style('color', nodeColor);
        e.style('background-color', toColor);
        e.style('line-color', toColor);
        e.style('target-arrow-color', toColor);
        e.style('opacity', 0.5);
    });
    target_element.neighborhood().each((e) => {
        // 이웃한 엣지와 노드
        e.style('font-size', Math.max(parseFloat(e.style('font-size')), fontActiveSize));
        e.style('color', nodeColor);
        e.style('opacity', 1);
    });
};
const setResetFocus = (target_cy) => {
    target_cy.nodes().forEach((target) => {
        const originalColor = target.data('nodeMyColor');
        target.style('background-color', originalColor);
        target.style('width', nodeSize);
        target.style('height', nodeSize);
        target.style('font-size', fontSize);
        target.style('color', nodeColor);
        target.style('opacity', 1);
    });
    target_cy.edges().forEach(function (target) {
        target.style('line-color', edgeColor);
        target.style('target-arrow-color', edgeColor);
        target.style('width', edgeWidth);
        target.style('arrow-scale', arrowScale);
        target.style('opacity', 1);
    });
};
const getNodeColor = (gradeLevel) => {
    switch (gradeLevel) {
        case '초1':
        case '초2':
            return 'yellow';
        case '초3':
        case '초4':
            return 'springGreen';
        case '초5':
        case '초6':
            return 'green';
        case '중1':
            return 'lightblue';
        case '중2':
            return 'dodgerblue';
        case '중3':
            return 'rgb(9, 106, 204)';
        case '수학':
            return 'lightpink';
        case '수1':
        case '수2':
            return 'hotpink';
        case '미적':
        case '확통':
        case '기하':
            return 'red';
        default:
            return 'gray';
    }
};
// 노드 속성에 따라 색상 변경
const changeNodeColor = (cy) => {
    cy.nodes().forEach((node) => {
        const nodeData = node.data();
        const nodeMyColor = getNodeColor(nodeData.conceptGradeLevel);
        node.data('nodeMyColor', nodeMyColor); // 노드의 초기 색상을 저장
        node.style('background-color', nodeMyColor);
    });
};

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
    if (cyElement.value) {
        cy = cytoscape({
            container: cyElement.value,
            elements: knowledgeSpace.value,
            style: [
                {
                    selector: 'node',
                    style: {
                        'background-color': nodeColor,
                        width: nodeSize,
                        height: nodeSize,
                        'font-size': fontSize,
                        color: nodeColor,
                        label: 'data(label)',
                        'text-margin-y': -2,
                        'text-wrap': 'wrap', // 텍스트 줄바꿈 설정
                        'text-max-width': '60px' // 텍스트 최대 가로 길이 설정
                    }
                },
                {
                    selector: 'edge',
                    style: {
                        width: edgeWidth,
                        'curve-style': 'bezier',
                        'line-color': edgeColor, //#ccc
                        'target-arrow-color': edgeColor, //#ccc
                        'target-arrow-shape': 'triangle',
                        'arrow-scale': arrowScale
                    }
                }
            ],
            layout: {
                name: 'klay',
                animate: false,
                gravityRangeCompound: 1.5,
                klay: {
                    spacing: 26
                },
                fit: true, //레이아웃을 컨테이너에 맞게 자동 조정
                tile: true // 타일형 레이아웃 (노드를 격자로 배치)
                // nodeDimensionsIncludeLabels: true,
                // avoidOverlap: true, // 겹치는 노드 및 레이블 방지
                // avoidOverlapPadding: 10 // 겹치는 것을 방지하기 위한 여백
            }
        });
        // 노드 속성에 따라 색상 변경
        changeNodeColor(cy);

        // 클릭한 id 추출 (상세보기에 뿌려주기 위해)
        cy.on('tap', 'node', (event) => {
            clickedNodeId.value = event.target.id();
        });

        // 마우스 인/아웃 하이라이트
        cy.on('tapstart mouseover', 'node', (e) => {
            setDimStyle(cy, {
                'background-color': dimColor,
                'line-color': dimColor,
                'source-arrow-color': dimColor,
                color: dimColor
            });
            setFocus(e.target, fromColor, toColor, edgeActiveWidth, arrowActiveScale);
        });
        cy.on('tapend mouseout', 'node', (e) => {
            const node = e.target;
            const originalColor = node.data('nodeMyColor');
            setResetFocus(e.cy);
        });
    }
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
    if (cy) {
        cy.destroy();
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
                    <div class="text-2xl font-semibold mx-2">선수지식 TREE</div>
                    <div class="mx-3"> 점을 클릭해보세요. 개념 상세보기를 할 수 있습니다.</div>
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
                        초등학교 : 초1,2 <i class="pi pi-circle-fill" style="color: yellow; font-size: 1.5rem"></i> 초3,4 <i class="pi pi-circle-fill" style="color: springgreen; font-size: 1.5rem"></i> 초5,6
                        <i class="pi pi-circle-fill" style="color: green; font-size: 1.5rem"></i>
                    </li>
                    <li class="text-600 font-medium mb-3">
                        중학교 : 중1 <i class="pi pi-circle-fill" style="color: skyblue; font-size: 1.5rem"></i> 중2 <i class="pi pi-circle-fill" style="color: dodgerblue; font-size: 1.5rem"></i> 중3
                        <i class="pi pi-circle-fill" style="color: rgb(9, 106, 204); font-size: 1.5rem"></i>
                    </li>
                    <li class="text-600 font-medium">
                        고등학교 : 수학(상/하) <i class="pi pi-circle-fill" style="color: lightpink; font-size: 1.5rem"></i> 수&#8544;,수&#8545; <i class="pi pi-circle-fill" style="color: hotpink; font-size: 1.5rem"></i> 미적,기하,확통
                        <i class="pi pi-circle-fill" style="color: red; font-size: 1.5rem"></i>
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
