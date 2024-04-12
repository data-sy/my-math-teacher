<script setup>
import { onMounted, ref, watch, onBeforeUnmount } from 'vue';
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
const sortedResultList = ref([]);
const userTestId = ref(null);
// 확률 값을 가져오는 함수
const getProbability = (item) => item.probability;
// 확률 값을 기준으로 정렬하는 함수
const sortDataByProbability = (data) => {
    return data.slice().sort((a, b) => getProbability(a) - getProbability(b));
};
// severity 값을 할당하는 함수
const getSeverity = (data) => {
    const totalItems = data.length;
    data.forEach((item, index) => {
        if (index < totalItems / 3) {
            item.severity = '상';
        } else if (index < (totalItems * 2) / 3) {
            item.severity = '중';
        } else {
            item.severity = '하';
        }
    });
};
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
                    sortedResultList.value = sortDataByProbability(resultList.value);
                    getSeverity(sortedResultList.value);
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
// 리팩토링) 기록 페이지에서 넘어왔다면 학습지 목록에 가상의 클릭 이벤트 추가하기
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
                            name: representativeItem.conceptName
                        };
                    }
                });
                sortedResultList.value = sortDataByProbability(resultList.value);
                getSeverity(sortedResultList.value);
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
// 선수지식 TREE 셋팅
const getNodeColor = (nodeData) => {
  const gradeLevel = nodeData.conceptGradeLevel;
  switch (gradeLevel){
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
}
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
    }
    );
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
    }
    );
}
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
}
// 노드 속성에 따라 색상 변경
const changeNodeColor = (cy) => {
  cy.nodes().forEach(node => {
      const nodeData = node.data();
      const nodeMyColor = getNodeColor(nodeData); 
      node.data('nodeMyColor', nodeMyColor); // 노드의 초기 색상을 저장
      node.style('background-color', nodeMyColor);
    }
  );
};

// 클릭 시 선수지식 TREE 보여주기
const selectedConceptId = ref(null);
const selectConceptId = (conceptId) => {
    selectedConceptId.value = conceptId;
}
watch(selectedConceptId, async (newValue) => {
    // conceptId가 선택될 때 마다 초기화되어야 하므로 이 안에 넣음
    const uniqueConceptIds = new Set();
    const knowledgeSpace = [];
    try {
        const nodesEndpoint = `/api/v1/concepts/nodes/${newValue}`;
        const nodesResponse = await api.get(nodesEndpoint);
        const edgesEndpoint = `/api/v1/concepts/edges/${newValue}`;
        const edgesResponse = await api.get(edgesEndpoint);
        // nodesResponse -> knowledgeSpace의 data
        nodesResponse.forEach(node => {
            uniqueConceptIds.add(node.conceptId);
        });
        // 중복이 제거된 conceptId를 가지고 knowledgeSpace에 데이터 추가
        uniqueConceptIds.forEach(uniqueConceptId => {
        const filteredNode = nodesResponse.find(node => node.conceptId === uniqueConceptId);
        if (filteredNode) {
            knowledgeSpace.push({
            data: {
                id: filteredNode.conceptId.toString(),
                label: filteredNode.conceptName,
                conceptGradeLevel: filteredNode.conceptGradeLevel
            }
            });
        }
        });
        // edgesResponse -> knowledgeSpace의 data
        edgesResponse.forEach(edge => {
            // edge의 source가 nodes의 conceptId에 있는지 확인 (나중에 미리 백단에서 걸러오는 방법으로 리팩토링)
            const sourceExists = nodesResponse.some(node => {
                return node.conceptId === parseInt(edge.data.source);
            });
            // target이 nodes 안에 있을 경우만 추가
            if (sourceExists) {
                knowledgeSpace.push(edge);
            }
        });
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
    if (cyElement.value) {
        cy = cytoscape({
        container: cyElement.value,
        elements: knowledgeSpace,
        style: [
            {
            selector: 'node',
            style: {
                'background-color': nodeColor,
                'width': nodeSize,
                'height': nodeSize,
                'font-size': fontSize,
                'color': nodeColor,
                'label': 'data(label)',
                'text-margin-y': -2,
                'text-wrap': 'wrap', // 텍스트 줄바꿈 설정
                'text-max-width': '60px', // 텍스트 최대 가로 길이 설정
            }
            },
            {
            selector: 'edge',
            style: {
                'width': edgeWidth,
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
            spacing: 26, 
            },
            fit: true, //레이아웃을 컨테이너에 맞게 자동 조정
            tile: true // 타일형 레이아웃 (노드를 격자로 배치)
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
            'color': dimColor
        });
        setFocus(e.target, fromColor, toColor, edgeActiveWidth, arrowActiveScale);
        });
        cy.on('tapend mouseout', 'node', (e) => {
        const node = e.target;
        const originalColor = node.data('nodeMyColor');
        setResetFocus(e.cy);
        });
    }
});
// 컴포넌트 파기 시 Cytoscape 인스턴스 파기
onBeforeUnmount(() => {
  if (cy) {
    cy.destroy();
  }
});
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
                        <Column field="priority" header="ㅇ">
                            <template #body="slotProps">
                                <Tag :value="slotProps.data.priority" :severity="getSeverity(slotProps.data.severity)" />
                            </template>
                        </Column>
                        <Column field="toConceptDepth" header="선수지식 깊이" style="min-width: 20px"></Column>
                        <Column field="conceptName" header="개념" style="min-width: 200px"></Column>
                        <Column field="level" header="학교-학년-학기" style="min-width: 120px"></Column>
                        <Column field="chapter" header="단원" style="min-width: 300px"></Column>
                        <template #groupheader="slotProps">
                            <div class="flex align-items-center gap-2 text-xl text-primary">
                                <span class="font-bold mx-2"> [문항 {{ slotProps.data.testItemNumber }}번] </span>
                                <span>{{ slotProps.data.representative.name }}</span>
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
                <div>
                    <div ref="cyElement" style="height: 400px; width: 100%;"></div>
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
