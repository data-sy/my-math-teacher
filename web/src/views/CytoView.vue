<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import cytoscape from 'cytoscape';

const router = useRouter()
const receivedData = ref('')
const dataToSend = history.state.dataToSend;

// onMounted(() => {
//   if (dataToSend) {
//     console.log(dataToSend);
//     receivedData.value = dataToSend
//     // console.log("receivedData", receivedData);
//   }
// })

receivedData.value = { 
  "conceptId": 2119,
  "nodes": [
  {
        "conceptId": 2119,
        "conceptName": "사다리꼴",
        "conceptDescription": "사다리꼴: 평행한 변이 한 쌍이라도 있는 사각형",
        "conceptSchoolLevel": "초등",
        "conceptGradeLevel": "초4",
        "conceptSemester": "2학기",
        "conceptChapterId": 280,
        "conceptChapterName": "사다리꼴을 알아볼까요",
        "conceptChapterMain": null,
        "conceptChapterSub": "사각형",
        "conceptAchievementId": 158,
        "conceptAchievementName": "여러 가지 모양의 사각형에 대한 분류 활동을 통하여 사다리꼴, 평행사변형, 마름모를 알고, 그 성질을 이해한다.",
        "conceptSection": "['도형, 기하']"
    },
      { "conceptId": 342, "conceptName": "직사각형", "conceptDescription": "네 각이 모두 직각인 사각형을 직사각형이라고 합니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초3", "conceptSemester": "1학기", "conceptChapterId": 156, "conceptChapterName": "직사각형을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "평면도형", "conceptAchievementId": 149, "conceptAchievementName": "각과 직각의 의미를 이해하고, 생활 주변에서 예를 찾을 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 6804, "conceptName": "네모 모양 알아 보기", "conceptDescription": "네모모양은 뾰족한 곳이 4군데 편평한 곳이 4군데입니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초1", "conceptSemester": "2학기", "conceptChapterId": 47, "conceptChapterName": "여러 가지 모양을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "여러 가지 모양", "conceptAchievementId": 145, "conceptAchievementName": "교실 및 생활 주변에서 여러 가지 물건을 관찰하여 삼각형, 사각형, 원의 모양을 찾고, 그것들을 이용하여 여러 가지 모양을 꾸밀 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 332, "conceptName": "각", "conceptDescription": "한 점에서 그은 두 반직선으로 이루어진 도형을 '각'이라고 합니다. 반직선 ㄴㄱ과 반직선 ㄴㄷ으로 이루어진 각을 '각 ㄱㄴㄷ' 또는 '각 ㄷㄴㄱ'이라고 하고, 이때 점 ㄴ을 각의 꼭짓점이라 합니다. 반직선 ㄴㄱ과 반직선 ㄴㄷ을 각의 '변'이라고 하고, 이 변을 '변 ㄴㄱ'과 '변 ㄴㄷ'이라고 합니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초3", "conceptSemester": "1학기", "conceptChapterId": 153, "conceptChapterName": "각을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "평면도형", "conceptAchievementId": 149, "conceptAchievementName": "각과 직각의 의미를 이해하고, 생활 주변에서 예를 찾을 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 346, "conceptName": "정사각형", "conceptDescription": "네 각이 모두 직각이고 네 변의 길이가 모두 같은 사각형을 '정사각형'이라고 합니다.\\n(참고) 정사각형은 네 각이 모두 직각이므로 직사각형이라고 할 수 있습니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초3", "conceptSemester": "1학기", "conceptChapterId": 157, "conceptChapterName": "정사각형을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "평면도형", "conceptAchievementId": 149, "conceptAchievementName": "각과 직각의 의미를 이해하고, 생활 주변에서 예를 찾을 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 6804, "conceptName": "네모 모양 알아 보기", "conceptDescription": "네모모양은 뾰족한 곳이 4군데 편평한 곳이 4군데입니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초1", "conceptSemester": "2학기", "conceptChapterId": 47, "conceptChapterName": "여러 가지 모양을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "여러 가지 모양", "conceptAchievementId": 145, "conceptAchievementName": "교실 및 생활 주변에서 여러 가지 물건을 관찰하여 삼각형, 사각형, 원의 모양을 찾고, 그것들을 이용하여 여러 가지 모양을 꾸밀 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 332, "conceptName": "각", "conceptDescription": "한 점에서 그은 두 반직선으로 이루어진 도형을 '각'이라고 합니다. 반직선 ㄴㄱ과 반직선 ㄴㄷ으로 이루어진 각을 '각 ㄱㄴㄷ' 또는 '각 ㄷㄴㄱ'이라고 하고, 이때 점 ㄴ을 각의 꼭짓점이라 합니다. 반직선 ㄴㄱ과 반직선 ㄴㄷ을 각의 '변'이라고 하고, 이 변을 '변 ㄴㄱ'과 '변 ㄴㄷ'이라고 합니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초3", "conceptSemester": "1학기", "conceptChapterId": 153, "conceptChapterName": "각을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "평면도형", "conceptAchievementId": 149, "conceptAchievementName": "각과 직각의 의미를 이해하고, 생활 주변에서 예를 찾을 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 342, "conceptName": "직사각형", "conceptDescription": "네 각이 모두 직각인 사각형을 직사각형이라고 합니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초3", "conceptSemester": "1학기", "conceptChapterId": 156, "conceptChapterName": "직사각형을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "평면도형", "conceptAchievementId": 149, "conceptAchievementName": "각과 직각의 의미를 이해하고, 생활 주변에서 예를 찾을 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 6804, "conceptName": "네모 모양 알아 보기", "conceptDescription": "네모모양은 뾰족한 곳이 4군데 편평한 곳이 4군데입니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초1", "conceptSemester": "2학기", "conceptChapterId": 47, "conceptChapterName": "여러 가지 모양을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "여러 가지 모양", "conceptAchievementId": 145, "conceptAchievementName": "교실 및 생활 주변에서 여러 가지 물건을 관찰하여 삼각형, 사각형, 원의 모양을 찾고, 그것들을 이용하여 여러 가지 모양을 꾸밀 수 있다.", "conceptSection": "['도형, 기하']" }, 
      { "conceptId": 332, "conceptName": "각", "conceptDescription": "한 점에서 그은 두 반직선으로 이루어진 도형을 '각'이라고 합니다. 반직선 ㄴㄱ과 반직선 ㄴㄷ으로 이루어진 각을 '각 ㄱㄴㄷ' 또는 '각 ㄷㄴㄱ'이라고 하고, 이때 점 ㄴ을 각의 꼭짓점이라 합니다. 반직선 ㄴㄱ과 반직선 ㄴㄷ을 각의 '변'이라고 하고, 이 변을 '변 ㄴㄱ'과 '변 ㄴㄷ'이라고 합니다.", "conceptSchoolLevel": "초등", "conceptGradeLevel": "초3", "conceptSemester": "1학기", "conceptChapterId": 153, "conceptChapterName": "각을 알아볼까요", "conceptChapterMain": null, "conceptChapterSub": "평면도형", "conceptAchievementId": 149, "conceptAchievementName": "각과 직각의 의미를 이해하고, 생활 주변에서 예를 찾을 수 있다.", "conceptSection": "['도형, 기하']" } 
    ], 
  "edges": [ 
    { "data": { "id": "153", "source": "6804", "target": "342" } }, 
    { "data": { "id": "2272", "source": "332", "target": "342" } }, 
    { "data": { "id": "155", "source": "6804", "target": "346" } }, 
    { "data": { "id": "2273", "source": "332", "target": "346" } }, 
    { "data": { "id": "2274", "source": "342", "target": "346" } }, 
    { "data": { "id": "918", "source": "342", "target": "2119" } }, 
    { "data": { "id": "919", "source": "346", "target": "2119" } } 
  ] 
}

const knowledgeSpace = [];
// nodes를 knowledgeSpace 변환하는 반복문
// receivedData.value.nodes.forEach(node => {
//   knowledgeSpace.push({
//     data: {
//       id: node.conceptId.toString(), // conceptId를 id로 사용
//       label: node.conceptName // conceptName을 label로 사용
//     }
//   });
// });
const uniqueConceptIds = new Set();
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
        label: filteredNode.conceptName
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

const cyElement = ref(null);
let cy = null;

onMounted(() => {
  cy = cytoscape({
    container: cyElement.value,
    elements: knowledgeSpace,
    // elements: [
    //   // 그래프 요소들을 정의합니다. (노드 데이터에  position: { x: 100, y: 100 })
    //   { data: { id: 'node1', label: 'Node 1' } },
    //   { data: { id: 'node2', label: 'Node 2' } },
    //   { data: { id: 'edge1', source: 'node1', target: 'node2' } }
    // ],
    style: [
      // 그래프 스타일을 정의합니다.
      {
        selector: 'node',
        style: {
          'background-color': '#666',
          'label': 'data(label)'
        }
      },
      {
        selector: 'edge',
        style: {
          'width': 3,
          // 화살표
          'curve-style': 'bezier',
          'line-color': '#ccc',
          'target-arrow-color': '#ccc',
          'target-arrow-shape': 'triangle'
        }
      }
    ]
  });
});

// 컴포넌트 파기 시 Cytoscape 인스턴스 파기
onBeforeUnmount(() => {
  if (cy) {
    cy.destroy();
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
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                <h5> 단위개념 상세보기 </h5>
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
