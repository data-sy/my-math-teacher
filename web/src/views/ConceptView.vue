<script setup>
import { ref, computed, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useConceptGraph } from '@/composables/useConceptGraph.js';
import { VMarkdownView } from 'vue3-markdown';
import 'vue3-markdown/dist/style.css';

const router = useRouter();
const api = useApi();

// ─── 진입: 검색(자동완성) + 학교급 선택 필터 ───
const schoolLevels = [
    { name: '전체', value: null },
    { name: '초등', value: '초등' },
    { name: '중등', value: '중등' },
    { name: '고등', value: '고등' }
];
const schoolFilter = ref(schoolLevels[0]);
const searchQuery = ref(null);
const suggestions = ref([]);

const onSearch = async (event) => {
    const q = (event.query || '').trim();
    if (!q) {
        suggestions.value = [];
        return;
    }
    try {
        const params = new URLSearchParams({ q, limit: '10' });
        if (schoolFilter.value?.value) params.append('schoolLevel', schoolFilter.value.value);
        suggestions.value = await api.get(`/api/v1/concepts/search?${params.toString()}`);
    } catch (err) {
        console.error('개념 검색 실패:', err);
        suggestions.value = [];
    }
};

const onSelect = (event) => {
    const picked = event.value;
    if (picked?.conceptId != null) {
        selectConcept(picked.conceptId);
    }
};

// ─── 그래프 (렌더링 레이어는 useConceptGraph 컴포저블이 소유) ───
const cyElement = ref(null);
const { initGraph, destroy: destroyGraph, GRADE_COLORS, zoomIn, zoomOut, fit, reset } = useConceptGraph();

const knowledgeSpace = ref([]);
const nodesResponse = ref(null);

// 우측 디테일 = 현재 포커스된 개념(검색으로 고른 루트 또는 그래프에서 클릭한 노드).
const focusedConcept = ref(null);
const hasGraph = ref(false);

// 개념설명 마크다운의 이스케이프 보정(기존 거동 유지) + null 가드.
const normalizeDescription = (concept) => {
    if (concept?.conceptDescription) {
        concept.conceptDescription = concept.conceptDescription.replace(/\\n/g, '\n').replace(/\ne/g, '\\ne');
    }
    return concept;
};

// breadcrumb: "초등 1 · 1학기 > 단원 > 개념"
const breadcrumb = computed(() => {
    const c = focusedConcept.value;
    if (!c) return '';
    return [c.conceptSchoolLevel, c.conceptGradeLevel, c.conceptChapterName, c.conceptName].filter(Boolean).join(' › ');
});

const clearCy = () => {
    knowledgeSpace.value = [];
    destroyGraph();
};

// 검색 결과 선택 → 상세 로드(우측) + 그래프 자동 렌더(버튼 없이).
const selectConcept = async (conceptId) => {
    try {
        const detail = await api.get(`/api/v1/concepts/${conceptId}`);
        focusedConcept.value = normalizeDescription(detail);
    } catch (err) {
        console.error('개념 상세 로딩 실패:', err);
    }
    await loadGraph(conceptId);
};

// 선수지식 노드/엣지 로드 후 cytoscape 렌더. 매 호출마다 새로 조립(누적 버그 방지).
const loadGraph = async (conceptId) => {
    clearCy();
    try {
        nodesResponse.value = await api.get(`/api/v1/concepts/nodes/${conceptId}`);
        const edgesResponse = await api.get(`/api/v1/concepts/edges/${conceptId}`);
        const seen = new Set();
        const elements = [];
        nodesResponse.value.forEach((node) => {
            if (!seen.has(node.conceptId)) {
                seen.add(node.conceptId);
                elements.push({
                    data: {
                        id: node.conceptId.toString(),
                        label: node.conceptName,
                        conceptGradeLevel: node.conceptGradeLevel
                    }
                });
            }
        });
        edgesResponse.forEach((edge) => {
            const sourceExists = nodesResponse.value.some((node) => node.conceptId === parseInt(edge.data.source));
            if (sourceExists) elements.push(edge);
        });
        knowledgeSpace.value = elements;
    } catch (err) {
        console.error('그래프 데이터 로딩 실패:', err);
    }
    initGraph(cyElement.value, knowledgeSpace.value, {
        onTapNode: showNode
    });
    hasGraph.value = knowledgeSpace.value.length > 0;
};

// 그래프 노드 클릭 → 우측 디테일을 그 노드로 갱신.
const showNode = (id) => {
    const node = nodesResponse.value?.find((n) => n.conceptId === parseInt(id));
    if (node) {
        focusedConcept.value = normalizeDescription(node);
    }
};

// 진단 CTA — 기존 진단 진입(/diagnosis)으로. (개념별 맞춤 진단은 별도 백로그)
const goToDiagnosis = () => router.push({ path: '/diagnosis' });

onBeforeUnmount(() => {
    clearCy();
});
</script>

<template>
    <div class="concept-view">
        <!-- 진입 한 줄: 학교급 필터 + 개념 검색 + breadcrumb -->
        <div class="entry-bar card">
            <SelectButton v-model="schoolFilter" :options="schoolLevels" optionLabel="name" :allowEmpty="false" class="school-filter" />
            <span class="p-input-icon-left search-box">
                <i class="pi pi-search" />
                <AutoComplete v-model="searchQuery" :suggestions="suggestions" optionLabel="conceptName" :delay="300" placeholder="개념 이름으로 검색 (예: 이차방정식)" @complete="onSearch" @item-select="onSelect">
                    <template #option="{ option }">
                        <div class="sugg">
                            <span class="sugg-name">{{ option.conceptName }}</span>
                            <span class="sugg-meta t-caption">{{ [option.conceptSchoolLevel, option.conceptGradeLevel, option.conceptChapterName].filter(Boolean).join(' · ') }}</span>
                        </div>
                    </template>
                </AutoComplete>
            </span>
            <span v-if="breadcrumb" class="breadcrumb t-caption"
                >현재: <b>{{ breadcrumb }}</b></span
            >
        </div>

        <!-- 2-pane: 좌 그래프 / 우 선택개념 디테일 -->
        <div class="pane">
            <div class="card graph-card">
                <div class="graph-head">
                    <span class="t-subheading">선수지식 지도</span>
                    <div class="graph-ctrls">
                        <Button icon="pi pi-plus" text rounded aria-label="확대" @click="zoomIn" />
                        <Button icon="pi pi-minus" text rounded aria-label="축소" @click="zoomOut" />
                        <Button icon="pi pi-expand" text rounded aria-label="전체보기" @click="fit" />
                        <Button icon="pi pi-refresh" text rounded aria-label="리셋" @click="reset" />
                    </div>
                </div>
                <div class="graph-canvas-wrap">
                    <div ref="cyElement" class="graph-canvas"></div>
                    <div v-if="!hasGraph" class="graph-empty t-body">
                        <i class="pi pi-sitemap" style="font-size: 2rem; opacity: 0.4" />
                        <p>개념을 검색하면 선수지식 지도가 그려져요.</p>
                        <p class="t-caption">점을 클릭하면 오른쪽에 상세가 표시됩니다.</p>
                    </div>
                </div>
                <ul class="legend t-caption">
                    <li>
                        초등 <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['초1'] }"></i><i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['초3'] }"></i
                        ><i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['초5'] }"></i> 중등 <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['중1'] }"></i><i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['중2'] }"></i
                        ><i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['중3'] }"></i> 고등 <i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['수학'] }"></i><i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['수1'] }"></i
                        ><i class="pi pi-circle-fill" :style="{ color: GRADE_COLORS['미적'] }"></i>
                        <span class="legend-note">(명도 = 학년)</span>
                    </li>
                </ul>
            </div>

            <div class="card detail-card">
                <div v-if="focusedConcept" class="detail">
                    <VMarkdownView :content="focusedConcept.conceptName" class="t-heading detail-title" />
                    <div class="t-caption detail-sub">{{ [focusedConcept.conceptSchoolLevel, focusedConcept.conceptGradeLevel, focusedConcept.conceptSemester].filter(Boolean).join(' · ') }}</div>
                    <dl class="detail-list">
                        <dt class="t-caption">대-중-소단원</dt>
                        <dd class="t-body">{{ [focusedConcept.conceptChapterMain, focusedConcept.conceptChapterSub, focusedConcept.conceptChapterName].filter(Boolean).join(' - ') }}</dd>
                        <dt class="t-caption">성취기준</dt>
                        <dd class="t-body">{{ focusedConcept.conceptAchievementName }}</dd>
                        <dt class="t-caption">개념 설명</dt>
                        <dd class="t-body"><VMarkdownView :content="focusedConcept.conceptDescription" /></dd>
                    </dl>
                    <Button label="이 개념 진단받기" icon="pi pi-arrow-right" iconPos="right" class="cta" @click="goToDiagnosis" />
                </div>
                <div v-else class="detail detail-placeholder t-body">
                    <i class="pi pi-book" style="font-size: 1.6rem; opacity: 0.4" />
                    <p>선택한 개념의 상세가 여기에 표시됩니다.</p>
                    <p class="t-caption">위에서 개념을 검색하거나, 지도의 점을 클릭하세요.</p>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.entry-bar {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    flex-wrap: wrap;
}
.search-box {
    flex: 1;
    min-width: 240px;
}
.search-box :deep(.p-autocomplete) {
    width: 100%;
}
.search-box :deep(.p-autocomplete-input) {
    width: 100%;
    padding-left: 2.5rem;
}
.breadcrumb {
    margin-left: auto;
}
.breadcrumb b {
    color: var(--mmt-brand);
}
.sugg {
    display: flex;
    flex-direction: column;
}
.sugg-meta {
    margin-top: 1px;
}

.pane {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: 1rem;
    margin-top: 1rem;
}
.graph-card {
    display: flex;
    flex-direction: column;
}
.graph-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 0.5rem;
}
.graph-ctrls {
    display: flex;
    gap: 0.25rem;
}
.graph-canvas-wrap {
    position: relative;
    min-height: 440px;
}
.graph-canvas {
    width: 100%;
    height: 440px;
}
.graph-empty {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.25rem;
    text-align: center;
    color: var(--mmt-text-muted);
    pointer-events: none;
}
.graph-empty p {
    margin: 0;
}
.legend {
    margin: 0.75rem 0 0;
    padding: 0;
    list-style: none;
    color: var(--mmt-text-muted);
}
.legend .pi-circle-fill {
    font-size: 0.9rem;
    margin: 0 1px;
    vertical-align: middle;
}
.legend-note {
    margin-left: 0.5rem;
}

.detail-card {
    align-self: start;
}
.detail-title {
    margin-bottom: 0.1rem;
}
.detail-sub {
    margin-bottom: 1rem;
}
.detail-list {
    margin: 0;
}
.detail-list dt {
    margin-top: 0.85rem;
}
.detail-list dd {
    margin: 0.15rem 0 0;
}
.cta {
    width: 100%;
    margin-top: 1.25rem;
}
.detail-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 0.25rem;
    color: var(--mmt-text-muted);
    padding: 1.5rem 0;
}
.detail-placeholder p {
    margin: 0;
}

@media (max-width: 960px) {
    .pane {
        grid-template-columns: 1fr;
    }
    .detail-card {
        align-self: stretch;
    }
}
</style>
