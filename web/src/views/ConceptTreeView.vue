<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import cytoscape from 'cytoscape';
import klay from 'cytoscape-klay';

cytoscape.use(klay);
const cyElement = ref(null);
let cy = null;

const router = useRouter()

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

const dataToSend = history.state.dataToSend;
const receivedData = ref('')
const uniqueConceptIds = new Set();
const knowledgeSpace = [];
const clickedNodeId = ref('');
const conceptDetail = ref(null);
onMounted(() => {
  if (dataToSend) {
    receivedData.value = dataToSend
  }
  if (receivedData.value) {
    // 해당 concept
    conceptDetail.value = receivedData.value.nodes.find(node => node.conceptId === receivedData.value.conceptId);
    // nodes -> knowledgeSpace의 data
    receivedData.value.nodes.forEach(node => {
      uniqueConceptIds.add(node.conceptId);
    });
      // 중복이 제거된 conceptId를 가지고 knowledgeSpace에 데이터 추가
    uniqueConceptIds.forEach(uniqueConceptId => {
      const filteredNode = receivedData.value.nodes.find(node => node.conceptId === uniqueConceptId);
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
    // edges -> knowledgeSpace의 data
    receivedData.value.edges.forEach(edge => {
      // edge의 source가 nodes의 conceptId에 있는지 확인 (나중에 미리 백단에서 걸러오는 방법으로 리팩토링)
      const sourceExists = receivedData.value.nodes.some(node => {
        return node.conceptId === parseInt(edge.data.source);
      });
      // target이 nodes 안에 있을 경우만 추가
      if (sourceExists) {
        knowledgeSpace.push(edge);
      }
    });
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

const selectedNode = ref('')
// 노드 클릭 시 해당 노드의 데이터 화면에 보여주기
watch(clickedNodeId, (newValue) => {
  const selectedNodeId = parseInt(newValue);
  if (newValue && receivedData.value && receivedData.value.nodes) {
    selectedNode.value = receivedData.value.nodes.find(node => node.conceptId === selectedNodeId);
  }
});
// '이전' 버튼 (conceptlist로)
const goBack = () => {
  router.go(-1) // 또는 router.back()
}
// 홈으로
const goToHome = () => {
  try {
    router.push({ path: '/' }); 
  } catch (error) {
    console.error('에러 발생:', error);
  }
};
</script>

<template>
    <div class="grid p-fluid">
      <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between">
                    <div>
                        <div class="text-900 font-medium text-xl mb-3"> 여기는 선수지식 TREE를 볼 수 있는 공간이야. </div>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-600 font-medium mb-3"> 1. [선수지식 TREE] 설명서 </span>
                        <ul style="list-style-type: disc;">
                          <li class="text-600 font-medium mb-3"> 드래그를 하면 화면이 확대/축소 돼 </li>
                          <li class="text-600 font-medium mb-3"> 글씨가 겹쳐 보이면 점을 클릭&드래그 해 봐. 모양을 직접 수정할 수 있단다.</li>
                          <li class="text-600 font-medium mb-3"> 원하는 점에 마우스를 가져다 대면 직전,직후의 개념을 확실히 볼 수 있어.</li>
                          <li class="text-600 font-medium mb-5"> 원하는 점을 클릭하면 [선수지식 상세보기]에 자세한 정보가 뜰 거야.</li>
                        </ul>
                        <span class="block text-600 font-medium mb-3"> 2. 점의 색깔 의미 </span>
                        <ul style="list-style-type: disc;">
                          <li class="text-600 font-medium mb-3"> 초등학교 : 초1,2 <i class="pi pi-circle-fill" style="color: yellow; font-size: 1.5rem;"></i> 초3,4 <i class="pi pi-circle-fill" style="color: springgreen; font-size: 1.5rem;"></i> 초5,6 <i class="pi pi-circle-fill" style="color: green; font-size: 1.5rem;"></i> </li>
                          <li class="text-600 font-medium mb-3"> 중학교 : 중1 <i class="pi pi-circle-fill" style="color: skyblue; font-size: 1.5rem;"></i> 중2 <i class="pi pi-circle-fill" style="color: dodgerblue; font-size: 1.5rem;"></i> 중3 <i class="pi pi-circle-fill" style="color: rgb(9, 106, 204); font-size: 1.5rem;"></i></li>
                          <li class="text-600 font-medium"> 고등학교 : 수학(상/하) <i class="pi pi-circle-fill" style="color: lightpink; font-size: 1.5rem;"></i> 수&#8544;,수&#8545; <i class="pi pi-circle-fill" style="color: hotpink; font-size: 1.5rem;"></i> 미적,기하,확통 <i class="pi pi-circle-fill" style="color: red; font-size: 1.5rem;"></i> </li>
                        </ul>
                      </div>
                </div>
            </div>
        </div>
        <div class="col-12 lg:col-6">
            <div class="card">
                <h5> 선수단위개념 상세보기 </h5>
                <div class="surface-section" v-if="selectedNode"> 
                    <div class="font-medium text-4xl text-900 mb-3">{{ selectedNode.conceptName }}</div>
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
                            <div class="text-900 font-medium w-full md:w-9 md:flex-order-0 flex-order-1">{{ selectedNode.conceptDescription }}</div>
                        </li>
                    </ul>
                </div>
                <div class="surface-section" v-else>
                    <div class="font-medium text-3xl text-900 mb-3 text-blue-500"> 개념을 선택해주세요 </div>
                    <div class="text-500 mb-5">  </div>
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
                <h5> 단위개념 상세보기 </h5>
                <div class="surface-section" v-if="conceptDetail"> 
                    <div class="font-medium text-4xl text-900 mb-3">{{ conceptDetail.conceptName }}</div>
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
                            <div class="text-900 font-medium w-full md:w-9 md:flex-order-0 flex-order-1">{{ conceptDetail.conceptDescription }}</div>
                        </li>
                    </ul>
                </div>
              </div>
        </div>
        <div class="col-12">
            <div class="card">
                <h5> 선수지식 TREE </h5>
                <div>
                    <div ref="cyElement" style="height: 400px; width: 100%;"></div>
                </div>
            </div>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2 mb-5">
            <Button  @click="goBack" label="이전" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8"></div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToHome" label="홈으로" icon="pi pi-home" class="mr-2 mb-2"></Button>
        </div>     
    </div>
</template>
