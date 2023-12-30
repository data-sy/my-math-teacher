<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import cytoscape from 'cytoscape';
import klay from 'cytoscape-klay';

cytoscape.use(klay);

const router = useRouter()
const dataToSend = history.state.dataToSend;
const receivedData = ref('')
const knowledgeSpace = [];
const uniqueConceptIds = new Set();
const cyElement = ref(null);
let cy = null;
const clickedNodeId = ref('');
const conceptDetail = ref(null);

const getNodeColor = (nodeData) => {
  const gradeLevel = nodeData.conceptGradeLevel;
  switch (gradeLevel){
    case '초1':
    case '초2':
      return 'yellow'; // 초1일 때 빨간색 반환
    case '초3':
    case '초4':
      return 'SpringGreen'; // 초2일 때 파란색 반환
    case '초5':
    case '초6':
      return 'green'; // 초3일 때 초록색 반환
    case '중1':
      return 'lightblue'; // 초4일 때 주황색 반환
    case '중2':
      return 'skyblue'; // 초4일 때 주황색 반환
    case '중3':
      return 'dodgerblue'; // 초4일 때 주황색 반환
    case '수학':
      return 'lightpink'; // 초4일 때 주황색 반환  
    case '수1':
    case '수2':
      return 'HotPink'; // 초4일 때 주황색 반환
    case '미적':
    case '확통':
    case '기하':
      return 'red'; // 초4일 때 주황색 반환
    default:
      return 'gray'; // 해당하지 않는 경우 회색 반환 또는 다른 기본 색상 설정
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
const nodeColor = '#57606f'; //글씨색??
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
      const nodeMyColor = getNodeColor(nodeData); // 노드의 색상 결정 함수 호출
      node.data('nodeMyColor', nodeMyColor); // 노드의 초기 색상을 저장
      node.style('background-color', nodeMyColor);
    }
  );
};
// // 라벨의 위치 변경 (여러 상황에 유기적으로 대응할 수는 없어...)
// const setLabelPositionBasedOnConnection = (cy) => {
//     const processedNodes = new Set(); // 이미 처리된 노드를 추적하는 Set
//     cy.nodes().forEach((node) => {
//         if (processedNodes.has(node.id())) {
//             return; // 이미 처리된 노드는 스킵
//         }
//         const neighbors = node.neighborhood().nodes(); // 노드의 이웃(neighborhood) 가져오기
//         // console.log(neighbors);
//         // 현재 노드의 텍스트 라벨 위치
//         const currentNodeLabelPosition = parseInt(node.style('text-margin-y'));
//         // 현재 노드의 텍스트가 위에 있는 경우
//         if (currentNodeLabelPosition <= 0) {
//             neighbors.forEach((neighbor) => {
//                 // 이웃 노드의 텍스트 라벨 위치 확인 및 조정
//                 if (!processedNodes.has(neighbor.id())) {
//                     neighbor.style('text-margin-y', 16);
//                     processedNodes.add(neighbor.id()); // 이웃 노드를 이미 처리된 노드로 추가
//                 }
//             });
//             processedNodes.add(node.id()); // 현재 노드를 이미 처리된 노드로 추가
//         }
//         // 현재 노드의 텍스트가 아래에 있는 경우
//         else {
//             neighbors.forEach((neighbor) => {
//                 // 이웃 노드의 텍스트 라벨 위치 확인 및 조정
//                 if (!processedNodes.has(neighbor.id())) {
//                     neighbor.style('text-margin-y', -1);
//                     processedNodes.add(neighbor.id()); // 선행자 노드를 이미 처리된 노드로 추가
//                 }
//             });
//             processedNodes.add(node.id()); // 현재 노드를 이미 처리된 노드로 추가
//         }
//     });
// };

onMounted(() => {
  if (dataToSend) {
    // console.log(dataToSend);
    receivedData.value = dataToSend
    // console.log("receivedData", receivedData);
  }
  if (receivedData.value) {
    // 해당 concept
    conceptDetail.value = receivedData.value.nodes.find(node => node.conceptId === receivedData.value.conceptId);
    // nodes를 knowledgeSpace 변환하는 반복문
    receivedData.value.nodes.forEach(node => {
      // 중복된 conceptId를 Set에 추가
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
    // edges를 knowledgeSpace 변환
    // 나중에 백단에서 걸러서 오는 걸로 리팩토링
    receivedData.value.edges.forEach(edge => {
      // edge의 source가 nodes의 conceptId에 있는지 확인
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
        // 그래프 스타일을 정의합니다.
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
        gravityRangeCompound: 1.5, // 1.5
        klay: {
          spacing: 26, 
        },
        fit: true, //레이아웃을 컨테이너에 맞게 자동 조정
        tile: true // 타일형 레이아웃 (노드를 격자로 배치)
      }
    });
    // 노드 속성에 따라 색상 변경
    changeNodeColor(cy);
    // // 라벨의 위치 변경
    // setLabelPositionBasedOnConnection(cy);

    // 클릭한 id 추출 (상세보기에 뿌려주기 위해)
    cy.on('tap', 'node', (event) => {
        clickedNodeId.value = event.target.id();
    });

    // 마우스 인/아웃 하이라이트
    // cy.on('tap', function (e) {});
    cy.on('tapstart mouseover', 'node', (e) => {
      // console.log("in");
      setDimStyle(cy, {
        'background-color': dimColor,
        'line-color': dimColor,
        'source-arrow-color': dimColor,
        'color': dimColor
      });
      setFocus(e.target, fromColor, toColor, edgeActiveWidth, arrowActiveScale);
    });
    cy.on('tapend mouseout', 'node', (e) => {
      // console.log("out");
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
    if (selectedNode) {
      // 선택된 노드 데이터를 가지고 화면에 필요한 작업 수행
      // console.log(selectedNode.value);
      // 화면에 표시하는 로직을 여기에 추가
    }
  }
});

const goBack = () => {
  // 이전 페이지로 돌아가기
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
        <div class="col-12 xl:col-6">
            <div class="card">
                <h5> 선수단위개념 상세보기 </h5>
                <div class="surface-section" v-if="selectedNode"> <!-- conceptDetail자리에 testData 사용-->
                    <div class="font-medium text-4xl text-900 mb-3">{{ selectedNode.conceptName }}</div>
                    <div class="text-500 mb-5"></div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">학교-학년-학기</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">{{ selectedNode.conceptSchoolLevel }}-{{ selectedNode.conceptGradeLevel }}-{{ selectedNode.conceptSemester }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">단원</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">{{ selectedNode.conceptChapterMain }}-{{ selectedNode.conceptChapterSub }}-{{ selectedNode.conceptChapterName }}</div>
                        </li>
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">개념설명</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">{{ selectedNode.conceptDescription }}</div>
                        </li>
                        <!-- <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">영역</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">
                                <Chip v-for="section in conceptDetail.conceptSection" :label="section" class="mr-2" :key="section" />
                            </div>
                        </li> -->
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">{{ selectedNode.conceptAchievementName }}</div>
                        </li>
                    </ul>
                </div>
                <div class="surface-section" v-else>
                    <div class="font-medium text-3xl text-900 mb-3 text-blue-500"> 단위개념을 선택해주세요 </div>
                    <div class="text-500 mb-5">  </div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">개념설명</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1"></div>
                        </li>
                        <!-- <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">영역</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1"></div>
                        </li> -->
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1"></div>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                <h5> 단위개념 상세보기 </h5>
                <div class="surface-section" v-if="conceptDetail"> 
                    <div class="font-medium text-4xl text-900 mb-3">{{ conceptDetail.conceptName }}</div>
                    <div class="text-500 mb-5"></div>
                    <ul class="list-none p-0 m-0">
                        <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">개념설명</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">{{ conceptDetail.conceptDescription }}</div>
                        </li>
                        <!-- <li class="flex align-items-center py-3 px-2 border-top-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">영역</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">
                                <Chip v-for="section in conceptDetail.conceptSection" :label="section" class="mr-2" :key="section" />
                            </div>
                        </li> -->
                        <li class="flex align-items-center py-3 px-2 border-top-1 border-bottom-1 surface-border flex-wrap">
                            <div class="text-500 w-6 md:w-2 font-medium">성취기준</div>
                            <div class="text-900 w-full md:w-8 md:flex-order-0 flex-order-1">{{ conceptDetail.conceptAchievementName }}</div>
                        </li>
                    </ul>
                </div>
              </div>
        </div>
        <div class="col-12">
            <div class="card">
                <h5> 단위개념 TREE </h5>
                <!-- <p>받은 데이터: {{ receivedData }}</p> -->
                <div>
                    <div ref="cyElement" style="height: 400px; width: 100%;"></div>
                </div>
            </div>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button  @click="goBack" label="이전" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8">빈공간</div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button @click="goToHome" label="홈으로" icon="pi pi-home" class="mr-2 mb-2"></Button>
        </div>     
    </div>
</template>
