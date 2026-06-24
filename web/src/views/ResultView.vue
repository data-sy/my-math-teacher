<script setup>
import { onMounted, ref, watch, onBeforeUnmount, nextTick } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import { useConceptGraph } from '@/composables/useConceptGraph.js';
import { VMarkdownView } from 'vue3-markdown';
import 'vue3-markdown/dist/style.css';

const store = useStore();
const router = useRouter();
const api = useApi();

const dataToSend = history.state.dataToSend;
const receivedData = ref('');

const cyElement = ref(null);
const { initGraph, destroy: destroyGraph, GRADE_COLORS } = useConceptGraph();

const isLoggedIn = ref(false);
const listboxTest = ref(null);
const listboxTests = ref([]);
const resultList = ref([]);
const userTestId = ref(null);
const sortedResultList = ref([]);
// spec-04: 헤드라인 요약 + 우선순위 약점 카드용 파생 모델 (Task 2에서 렌더, 현재는 데이터만)
const weaknessCards = ref([]);
const resultSummary = ref({ itemCount: 0, weaknessCount: 0, mostUrgent: null });
const evidenceOpen = ref(false); // spec-04 Task 3: 근거(표·그래프·상세) progressive disclosure 토글
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
                const endpoint = `/api/v1/weakness-diagnosis/${userTestId.value}`;
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
                refreshDerivedResults();
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
        const endpoint = isLoggedIn.value ? `/api/v1/weakness-diagnosis/${userTestId.value}` : `/api/v1/weakness-diagnosis/sample/${userTestId.value}`;
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
                refreshDerivedResults();
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
// 시급도(상/중/하) → Badge severity 색. spec-04 §4.3 / D4
const getPriority = (status) => {
    switch (status) {
        case '상':
            return 'danger'; // 빨강
        case '중':
            return 'warning'; // 주황
        case '하':
            return 'info'; // 파랑(중립)
        default:
            return 'info';
    }
};

/////////////////// spec-04 · 약점 카드 모델 + 헤드라인 요약 (순수 가공) ///////////////////
// 시급도 절대 구간 (spec-04 §4.3). 제안 출발값 — 실데이터 mastery 분포 확인 후 도메인 보정 대상.
// probabilityPercent 가 0~100(percent) 스케일 전제. 런타임 검증에서 스케일·임계 확인.
const SEVERITY_BANDS = { high: 40, mid: 65 }; // mastery < 40 → 상, < 65 → 중, 그 외 → 하
const severityForMastery = (mastery) => {
    if (mastery < SEVERITY_BANDS.high) return '상';
    if (mastery < SEVERITY_BANDS.mid) return '중';
    return '하';
};
// 문항(testItemNumber) 그룹 → 카드 1개. (spec-04 §4.1, D1 문항 단위)
const buildWeaknessCards = (list) => {
    if (!Array.isArray(list) || list.length === 0) return [];
    const grouped = list.reduce((acc, row) => {
        (acc[row.testItemNumber] ??= []).push(row);
        return acc;
    }, {});
    const cards = Object.entries(grouped).map(([itemNumber, rows]) => {
        // 대표개념 = 문항이 직접 묻는 개념(depth 0). 누락 시 null 가드(PR #27 6098760 패턴).
        const representativeItem = rows.find((r) => r.toConceptDepth === 0) ?? null;
        // 가장 약한 선수지식 = probabilityPercent 최소 행 (채워야 할 곳). 카드 제목이 됨.
        const weakest = rows.reduce((min, r) => (r.probabilityPercent < min.probabilityPercent ? r : min), rows[0]);
        const mastery = weakest.probabilityPercent;
        return {
            testItemNumber: Number(itemNumber),
            representative: representativeItem ? { conceptId: representativeItem.conceptId, conceptName: representativeItem.conceptName } : null,
            weakest: { conceptId: weakest.conceptId, conceptName: weakest.conceptName, level: weakest.level, chapter: weakest.chapter },
            mastery,
            severity: severityForMastery(mastery),
            conceptCount: rows.length
        };
    });
    // 시급도순 = mastery 오름차순 (가장 약한 카드가 위로). spec-04 §4.1
    cards.sort((a, b) => a.mastery - b.mastery);
    return cards;
};
// 헤드라인 요약 (spec-04 §4.2, D2 가용 데이터만)
const buildResultSummary = (cards) => {
    return {
        itemCount: cards.length, // 분석된 N문항
        weaknessCount: cards.filter((c) => c.severity === '상' || c.severity === '중').length, // 약점 개념 M개(상·중)
        mostUrgent: cards.length > 0 ? cards[0] : null // 가장 급한 약점 (mastery 최저 = 정렬 선두)
    };
};
// resultList 로부터 파생 모델 일괄 재계산 (onMounted·watch 공용)
const refreshDerivedResults = () => {
    sortedResultList.value = sortProbaGroupByTestItemId(resultList.value);
    weaknessCards.value = buildWeaknessCards(resultList.value);
    resultSummary.value = buildResultSummary(weaknessCards.value);
};
// 표시 헬퍼 (Task 2) — 차트 미사용(D3), 숙련도는 숫자 + CSS 막대로
const masteryLabel = (m) => `${Math.round(m)}%`;
const masteryBarColor = (severity) => (severity === '상' ? '#e53935' : severity === '중' ? '#fb8c00' : '#43a047');
const masteryBarStyle = (card) => ({ width: `${Math.max(0, Math.min(100, card.mastery))}%`, backgroundColor: masteryBarColor(card.severity), height: '8px' });

/////////////////// ConceptTree ///////////////////
// 렌더링 레이어는 useConceptGraph 컴포저블이 소유 (위에서 initGraph/destroyGraph 구조분해)

// 문항 이름 클릭 시 : conceptTree 보여주기
const uniqueConceptIds = new Set();
const clickedNodeId = ref('');
const conceptDetail = ref(null);
const showTree = async (conceptId) => {
    evidenceOpen.value = true; // 근거 패널을 펼쳐 그래프 컨테이너가 렌더되게 함 (v-show)
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
    // cytoScape — 근거 패널 펼침이 DOM에 반영돼 컨테이너 크기가 잡힌 뒤 그래프 생성
    await nextTick();
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
// Cytoscape 인스턴스 파기
const clearCy = () => {
    knowledgeSpace.value = [];
    destroyGraph();
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
        acceptLabel: '확인',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: '안내', detail: '학습지를 선택하면 그에 따른 맞춤 학습지를 출제할 수 있습니다.', life: 3000 });
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
        acceptLabel: '확인',
        rejectLabel: ' ',
        accept: () => {
            toast.add({ severity: 'info', summary: '안내', detail: '로그인을 하면 결과를 볼 수 있습니다.', life: 3000 });
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
        <!-- spec-04 Task 2 · 헤드라인 요약 + 시급도순 우선순위 약점 카드 -->
        <div class="col-12" v-if="resultSummary.itemCount > 0">
            <div class="card">
                <h5>진단 결과 요약</h5>
                <p class="text-xl line-height-3 mb-4">
                    분석된 <b>{{ resultSummary.itemCount }}문항</b> 중 <span class="text-red-600 font-bold">{{ resultSummary.weaknessCount }}개 약점</span>이 있어요.
                    <template v-if="resultSummary.mostUrgent">
                        가장 급한 건 <b>{{ resultSummary.mostUrgent.weakest.conceptName }}</b
                        >(숙련도 {{ masteryLabel(resultSummary.mostUrgent.mastery) }})예요.
                    </template>
                </p>
                <div class="text-2xl font-semibold mb-3">지금 채워야 할 약점 <span class="text-500 text-base">(시급도순)</span></div>
                <div class="grid">
                    <div v-for="card in weaknessCards" :key="card.testItemNumber" class="col-12 md:col-6 xl:col-4">
                        <div class="surface-card border-1 surface-border border-round p-3 h-full flex flex-column">
                            <div class="flex align-items-center justify-content-between mb-2">
                                <Badge :value="card.severity" :severity="getPriority(card.severity)" size="large" />
                                <span class="text-500 text-sm">문항 {{ card.testItemNumber }}번</span>
                            </div>
                            <div class="text-xl font-bold mb-1">{{ card.weakest.conceptName }}</div>
                            <div v-if="card.representative" class="text-500 text-sm mb-3">대표개념 · {{ card.representative.conceptName }}</div>
                            <div class="text-sm mb-1">숙련도 {{ masteryLabel(card.mastery) }}</div>
                            <div class="surface-200 border-round mb-3" style="height: 8px">
                                <div class="border-round" :style="masteryBarStyle(card)"></div>
                            </div>
                            <div class="mt-auto">
                                <Button v-if="card.representative" @click="showTree(card.representative.conceptId)" label="선수지식 트리 보기" class="p-button-sm p-button-outlined w-full" />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <!-- spec-04 Task 4 · 빈 상태: 라벨만 있는 빈 표 대신 안내형 -->
        <div class="col-12" v-else>
            <div class="card text-center py-6">
                <i class="pi pi-chart-bar text-primary mb-3" style="font-size: 2.5rem"></i>
                <div class="text-2xl font-semibold mb-2">아직 분석할 결과가 없어요</div>
                <p class="text-500 text-lg m-0">왼쪽 목록에서 정오답을 기록한 학습지를 선택하면, 약점 진단 결과가 여기에 나타나요.</p>
            </div>
        </div>
        <div class="col-12 md:col-3 xl:col-3">
            <div class="card">
                <h5>정오답 기록한 학습지 목록</h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" />
            </div>
        </div>
        <!-- spec-04 Task 3 · 근거(표·그래프·상세)를 "근거 더보기"로 강등 (progressive disclosure) -->
        <div class="col-12">
            <Button
                :label="evidenceOpen ? '근거 접기' : '근거 더보기 (문항별 표 · 선수지식 그래프 · 개념 상세)'"
                :icon="evidenceOpen ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                class="p-button-text p-button-secondary"
                @click="evidenceOpen = !evidenceOpen"
            />
        </div>
        <div class="col-12" v-show="evidenceOpen">
            <div class="grid">
                <div class="col-12">
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
                            <Column field="representative.testItemNumber" header="대표 개념"></Column>
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
                                        <span>{{ slotProps.data.representative?.conceptName ?? slotProps.data.conceptName }}</span>
                                    </div>
                                    <div>
                                        <Button v-if="slotProps.data.representative" @click="showTree(slotProps.data.representative.conceptId)" label="선수지식 트리 누적해서 보기" class="p-button-outlined p-button-primary mr-2" />
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
                            <div class="text-2xl font-semibold mx-2">선수지식 트리</div>
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
            </div>
        </div>
        <!-- /근거 더보기 -->
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
