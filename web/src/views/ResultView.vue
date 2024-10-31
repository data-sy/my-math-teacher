<script setup>
import { onMounted, ref, watch, onBeforeUnmount } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import cytoscape from 'cytoscape';
import klay from 'cytoscape-klay';
import { VMarkdownView } from 'vue3-markdown';
import 'vue3-markdown/dist/style.css';

const store = useStore();
const router = useRouter();
const api = useApi();

const dataToSend = history.state.dataToSend;
const receivedData = ref('');

cytoscape.use(klay);
const cyElement = ref(null);
let cy = null;

const isLoggedIn = ref(false);
const listboxTest = ref(null);
const listboxTests = ref([]);
const resultList = ref([]);
const userTestId = ref(null);
const sortedResultList = ref([]);
const knowledgeSpace = ref([]); // clearCy;를 위해 앞에서 선언함
// 학습지 목록
onMounted(async () => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null;
    watch(
        () => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    );
    // // 로그인 했을 때는 유저의 목록, 아닐 때는 샘플 목록 가져오기
    // 이게 깔끔하긴 했지만.. 샘플 학습지에는 샘플이라는 이름을 달아주면 좋겠어서 그냥 원래대로 나눠서 적기
    // const endpoint = isLoggedIn.value ? '/api/v1/tests/user/is-record' : '/api/v1/tests/sample/is-record';
    // 로그인 했을 때는 user의 학습지
    if (isLoggedIn.value) {
        try {
            const endpoint = '/api/v1/tests/user/is-record';
            const response = await api.get(endpoint);
            listboxTests.value = response;
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
    // [기록하기] 화면에서 넘어왔을 때는 해당 학습지 선택
    if (dataToSend) {
        receivedData.value = dataToSend;
    }
    if (receivedData.value) {
        userTestId.value = receivedData.value.userTestId;
        if (userTestId.value !== null) {
            try {
                const endpoint = `/api/v1/weakness-diagnosis/result/${userTestId.value}`;
                const response = await api.get(endpoint);
                resultList.value = response;
                resultList.value.forEach((item) => {
                    const representativeItem = resultList.value.find((e) => e.testItemNumber === item.testItemNumber && e.toConceptDepth === 0);
                    if (representativeItem) {
                        item.representative = {
                            testItemNumber: item.testItemNumber,
                            conceptId: representativeItem.conceptId,
                            conceptName: representativeItem.conceptName
                        };
                    }
                });
                sortedResultList.value = sortProbaGroupByTestItemId(resultList.value);
            } catch (err) {
                console.error('데이터 생성 중 에러 발생:', err);
            }
        } else {
            console.log('사용자가 로그인하지 않았거나, 학습지를 선택하지 않았습니다.');
        }
    }
});
// 리팩토링) 기록 페이지에서 넘어왔다면 학습지 목록에 가상의 클릭 이벤트 추가하기 (목록에 선택된 학습지 보라색으로 체크되도록)
// 분석 결과
watch(listboxTest, async (newValue) => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null;
    clearCy();
    if (newValue !== null) {
        userTestId.value = newValue.userTestId;
        const endpoint = isLoggedIn.value ? `/api/v1/weakness-diagnosis/result/${userTestId.value}` : `/api/v1/weakness-diagnosis/result/sample/${userTestId.value}`;
        if (userTestId.value !== null) {
            try {
                const response = await api.get(endpoint);
                resultList.value = response;
                resultList.value.forEach((item) => {
                    const representativeItem = resultList.value.find((e) => e.testItemNumber === item.testItemNumber && e.toConceptDepth === 0);
                    if (representativeItem) {
                        item.representative = {
                            testItemNumber: item.testItemNumber,
                            conceptId: representativeItem.conceptId,
                            conceptName: representativeItem.conceptName
                        };
                    }
                });
                sortedResultList.value = sortProbaGroupByTestItemId(resultList.value);
            } catch (err) {
                console.error('데이터 생성 중 에러 발생:', err);
            }
        } else {
            console.log('사용자가 로그인하지 않았거나, 학습지를 선택하지 않았습니다.');
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
// 그룹별 (testItemNumber별) 시급도 할당
const sortProbaGroupByTestItemId = (array) => {
    // 그룹화
    const grouped = array.reduce((acc, obj) => {
        const keyValue = obj['testItemNumber'];
        if (!acc[keyValue]) {
            acc[keyValue] = [];
        }
        acc[keyValue].push(obj);
        return acc;
    }, {});
    // 그룹별 정렬 및 시급도 할당
    for (const group in grouped) {
        grouped[group].sort((a, b) => a.probabilityPercent - b.probabilityPercent);
        setPriority(grouped[group]); // 시급도 할당
    }
    // 그룹화 해제
    const flattenedArray = Object.values(grouped).flatMap((group) => group);
    // const flattenedArray = Object.values(grouped).reduce((acc, group) => {
    //     acc.push(...group);
    //     return acc;
    // }, []);

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

/////////////////// ConceptTree ///////////////////
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
// 문항 이름 클릭 시 : conceptTree 보여주기
const uniqueConceptIds = new Set();
const clickedNodeId = ref('');
const conceptDetail = ref(null);
const showTree = async (conceptId) => {
    // knowledgeSpace
    try {
        const nodesEndpoint = `/api/v1/concepts/nodes/${conceptId}`;
        const nodesResponse = await api.get(nodesEndpoint);
        const edgesEndpoint = `/api/v1/concepts/edges/${conceptId}`;
        const edgesResponse = await api.get(edgesEndpoint);
        // 해당 concept
        conceptDetail.value = nodesResponse.find((node) => node.conceptId === conceptId);
        conceptDetail.value.conceptDescription = conceptDetail.value.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne');
        // nodes -> knowledgeSpace의 data
        nodesResponse.forEach((node) => {
            uniqueConceptIds.add(node.conceptId);
        });
        // 중복이 제거된 conceptId를 가지고 knowledgeSpace에 데이터 추가
        uniqueConceptIds.forEach((uniqueConceptId) => {
            const filteredNode = nodesResponse.find((node) => node.conceptId === uniqueConceptId);
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
            const sourceExists = nodesResponse.some((node) => {
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
    // cytoScape
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
// Cytoscape 인스턴스 파기
const clearCy = () => {
    knowledgeSpace.value = [];
    if (cy) {
        cy.destroy();
    }
};
// 컴포넌트 파기 시 Cytoscape 인스턴스 파기
onBeforeUnmount(() => {
    clearCy();
});
const selectedNode1 = ref('');
const selectedNode2 = ref('');
const toggle = ref(true); // true이면 왼쪽에, false이면 오른쪽에
// 노드 클릭 시 해당 노드의 데이터 화면에 보여주기
watch(clickedNodeId, async (newValue) => {
    // console.log(newValue);
    const selectedNodeId = parseInt(newValue);
    if (selectedNodeId !== null) {
        try {
            const endpoint = `/api/v1/concepts/${selectedNodeId}`;
            const response = await api.get(endpoint);
            if (toggle.value) {
                selectedNode1.value = response;
                toggle.value = false;
            } else {
                selectedNode2.value = response;
                toggle.value = true;
            }
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    }
    if (selectedNode1.value !== '') {
        selectedNode1.value.conceptDescription = selectedNode1.value.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne');
    }
    if (selectedNode2.value !== '') {
        selectedNode2.value.conceptDescription = selectedNode2.value.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne');
    }
    // 스크롤
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
            toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 결과를 볼 수 있습니다.', life: 3000 });
        }
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
        userTestId: userTestId.value
    };
    router.push({
        name: 'personal',
        state: { dataToSend: data }
    });
};
</script>

<template>
    <div class="grid p-fluid">
        <!-- <div class="col-12 text-center">
            <div v-if="!isLoggedIn" class="text-orange-500 font-medium text-3xl">로그인이 필요한 페이지 입니다.</div>
        </div> -->
        <div class="col-12 md:col-3 xl:col-3">
            <div class="card">
                <h5>정오답 기록한 학습지 목록</h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" />
            </div>
        </div>
        <div class="col-12 md:col-9 xl:col-9">
            <div class="card">
                <h5>분석 결과</h5>
                <DataTable
                    :value="sortedResultList"
                    rowGroupMode="subheader"
                    groupRowsBy="representative.testItemNumber"
                    sortMode="single"
                    sortField="representative.testItemNumber"
                    :sortOrder="1"
                    scrollable
                    scrollHeight="30rem"
                    tableStyle="min-width: 50rem"
                >
                    <Column field="representative.testItemNumber" header="Representative"></Column>
                    <Column field="priority" header="시급도">
                        <template #body="slotProps">
                            <Badge :value="slotProps.data.priority" :severity="getPriority(slotProps.data.priority)" size="large" />
                        </template>
                    </Column>
                    <Column field="toConceptDepth" header="선수지식 깊이" style="min-width: 20px"></Column>
                    <Column field="conceptName" header="개념" style="min-width: 200px"></Column>
                    <Column field="level" header="학교-학년-학기" style="min-width: 120px"></Column>
                    <Column field="chapter" header="단원" style="min-width: 300px"></Column>
                    <template #groupheader="slotProps">
                        <div class="flex align-items-center gap-2 justify-content-around">
                            <div class="flex align-items-center gap-2 text-xl text-primary">
                                <span class="font-bold mx-2"> [문항 {{ slotProps.data.testItemNumber }}번] </span>
                                <span>{{ slotProps.data.representative.conceptName }}</span>
                            </div>
                            <div>
                                <Button @click="showTree(slotProps.data.representative.conceptId)" label="선수지식 TREE 누적해서 보기" class="p-button-outlined p-button-primary mr-2" />
                            </div>
                        </div>
                    </template>
                    <template #groupfooter="slotProps">
                        <div class="flex justify-content-end font-bold w-full">전체 개수 : {{ calculateResultTotal(slotProps.data.testItemNumber) }}</div>
                    </template>
                </DataTable>
                <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
            </div>
        </div>
        <div class="col-12">
            <div class="card" id="scroll-tree">
                <div class="flex align-items-center mb-5">
                    <div class="text-2xl font-semibold mx-2">선수지식 TREE</div>
                    <div><i class="pi pi-question-circle font-semibold mx-2" @mouseover="showSpec" @mouseout="hideSpec" style="font-size: 1.5rem"></i></div>
                    <div class="mx-6">
                        <Button @click="clearCy" label="화면 비우기" class="p-button-outlined p-button-primary mr-2" />
                    </div>
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
        <div class="col-12 lg:col-6">
            <div class="card" id="scroll-node">
                <h5>개념 상세보기 1</h5>
                <div class="surface-section" v-if="selectedNode1">
                    <div>
                        <VMarkdownView :content="selectedNode1.conceptName" class="font-medium text-4xl text-900 mb-3"></VMarkdownView>
                    </div>
                    <div class="text-500 mb-5"></div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">학교-학년-학기</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode1.conceptSchoolLevel }}-{{ selectedNode1.conceptGradeLevel }}-{{ selectedNode1.conceptSemester }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">대-중-소단원</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode1.conceptChapterMain }}-{{ selectedNode1.conceptChapterSub }}-{{ selectedNode1.conceptChapterName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode1.conceptAchievementName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-primary-500 w-6 md:w-3 font-xl font-bold">개념설명</div>
                            <div class="text-900 font-medium w-full md:w-9 md:flex-order-0 flex-order-1">
                                <VMarkdownView :content="selectedNode1.conceptDescription"></VMarkdownView>
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
        <div class="col-12 lg:col-6">
            <div class="card">
                <h5>개념 상세보기 2</h5>
                <div class="surface-section" v-if="selectedNode2">
                    <div>
                        <VMarkdownView :content="selectedNode2.conceptName" class="font-medium text-4xl text-900 mb-3"></VMarkdownView>
                    </div>
                    <div class="text-500 mb-5"></div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">학교-학년-학기</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode2.conceptSchoolLevel }}-{{ selectedNode2.conceptGradeLevel }}-{{ selectedNode2.conceptSemester }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">대-중-소단원</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode2.conceptChapterMain }}-{{ selectedNode2.conceptChapterSub }}-{{ selectedNode2.conceptChapterName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-3 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode2.conceptAchievementName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-primary-500 w-6 md:w-3 font-xl font-bold">개념설명</div>
                            <div class="text-900 font-medium w-full md:w-9 md:flex-order-0 flex-order-1">
                                <VMarkdownView :content="selectedNode2.conceptDescription"></VMarkdownView>
                            </div>
                        </li>
                    </ul>
                </div>
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
            <Button v-else @click="goToNextPage" label="맞춤 학습지 출제" class="mr-2 mb-2"></Button>
        </div>
    </div>
</template>

<style scoped>
/* .clickable {
  cursor: pointer;
  text-decoration: underline;
  color: blue;
} */
</style>
