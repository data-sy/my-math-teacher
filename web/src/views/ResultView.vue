<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router'
import { useApi } from '@/composables/api.js';

const router = useRouter()
const api = useApi();

const listboxTest = ref(null);
const listboxTests = ref([]);
// 학습지 목록
onMounted(async () => {
  try {
    const endpoint = "tests/user/is-record" 
    const response = await api.get(endpoint);
    listboxTests.value = response;
  } catch (error) {
    console.error('데이터 생성 중 에러 발생:', err);
  }
});
// 분석 결과
const resultList = ref([]);
const userTestId = ref(null);
watch(listboxTest, async (newValue) => {
    userTestId.value = newValue.userTestId;
    try {
        const endpoint = `/result/${userTestId.value}`;
        const response = await api.get(endpoint);
        resultList.value = response
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
});
const expandedRows = ref([]);
const expandAll = () => {
    expandedRows.value = resultList.value.filter((p) => p.probabilityId);
};
const collapseAll = () => {
    expandedRows.value = null;
};

</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between">
                    <div>
                        <div class="text-900 font-medium text-xl mb-3"> 여기는 AI 분석 결과를 보여주는 곳이야 </div>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-600 font-medium mb-3"> 1. [학습지 목록]에서 학습지 선택하기 </span>
                        <ul style="list-style-type: disc;">
                            <li class=mb-2> 정오답을 기록한 학습지 목록이야.</li>
                            <li class=mb-2> 선택한 학습지 이전에 작성한 모든 기록을 활용해서 분석해 줄 거야.</li>
                            <ul>
                                <li class=mb-2> 예) 3번 학습지를 선택하면 1, 2, 3번 학습지 결과를 활용</li>
                            </ul>
                        </ul>
                        <span class="block text-600 font-medium"> 2. [분석 결과보기] </span>
                        <ul style="list-style-type: disc;">
                            <li class=mb-2> 어떤 개념의 학습이 더 필요한지 </li>
                            <li class=mb-2> 개념을 클릭하면 그 개념의 [선수지식 TREE]를 보여줄 거야. </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card"> 
                <h5> 학습지 목록 </h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName"/>
            </div>
        </div>
        <div class="col-12 xl:col-9">
            <div class="card">
                <h5> 분석 결과 보기 </h5>
                <ScrollPanel :style="{ width: '100%', height: '35rem'}" :pt="{wrapper: {style: {'border-right': '10px solid var(--surface-ground)'}}, bary: 'hover:bg-primary-300 bg-primary-200 opacity-80'}"> 
                    <DataTable :value="resultList" v-model:expandedRows="expandedRows" dataKey="probabilityId" responsiveLayout="scroll">
                        <template #header>
                            <div>
                                <Button icon="pi pi-plus" label="Expand All" @click="expandAll" class="mr-2 mb-2" />
                                <Button icon="pi pi-minus" label="Collapse All" @click="collapseAll" class="mb-2" />
                            </div>
                        </template>
                        <Column :expander="true" headerStyle="min-width: 3rem" />
                        <Column field="testItemNumber" header="번호" :sortable="true">
                            <template #body="slotProps">
                                {{ slotProps.data.testItemNumber }}
                            </template>
                        </Column>
                        <Column field="conceptName" header="개념">
                            <template #body="slotProps">
                                {{ slotProps.data.conceptName }}
                            </template>
                        </Column>
                        <Column field="level" header="학교-학년-학기" :sortable="true">
                            <template #body="slotProps">
                                {{ slotProps.data.schoolLevel}}-{{ slotProps.data.gradeLevel}}-{{ slotProps.data.semester}}
                            </template>
                        </Column>
                        <Column field="chapter" header="단원" :sortable="true">
                            <template #body="slotProps">
                                {{ slotProps.data.chapterMain}}-{{ slotProps.data.chapterSub}}-{{ slotProps.data.chapterName}}
                            </template>
                        </Column>
                        <Column field="probability" header="확률" :sortable="true">
                            <template #body="slotProps">
                                {{ slotProps.data.probabilityPercent}} <!--상중하로 수정-->
                            </template>
                        </Column>
                        <template #expansion="slotProps">
                            <div class="p-3">
                                <h5> [{{ slotProps.data.conceptName }}]에서 파생된 학습 목록(선수지식 목록)</h5>
                                <DataTable :value="slotProps.data.prerequisiteList" responsiveLayout="scroll">
                                    <Column field="conceptName" header="개념" :sortable="true">
                                        <template #body="slotProps">
                                            {{ slotProps.data.conceptName }}
                                        </template>
                                    </Column>
                                    <Column field="depth" header="선수지식 깊이" :sortable="true">
                                        <template #body="slotProps">
                                            {{ slotProps.data.toConceptDepth }}
                                        </template>
                                    </Column>
                                    <Column field="level" header="학교-학년-학기" :sortable="true">
                                        <template #body="slotProps">
                                            {{ slotProps.data.schoolLevel}}-{{ slotProps.data.gradeLevel}}-{{ slotProps.data.semester}}
                                        </template>
                                    </Column>
                                    <Column field="chapter" header="단원" :sortable="true">
                                        <template #body="slotProps">
                                            {{ slotProps.data.chapterMain}}-{{ slotProps.data.chapterSub}}-{{ slotProps.data.chapterName}}
                                        </template>
                                    </Column>
                                    <Column field="probability" header="확률" :sortable="true">
                                        <template #body="slotProps">
                                            {{ slotProps.data.probabilityPercent}} <!--상중하로 수정-->
                                        </template>
                                    </Column>
                                    <Column headerStyle="width:4rem">
                                        <template #body>
                                            <Button icon="pi pi-search" />
                                        </template>
                                    </Column>
                                </DataTable>
                            </div>
                        </template>
                    </DataTable>
                <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up"></ScrollTop>
                </ScrollPanel>
            </div>
            <div class="card">
                <h5> 선수지식 TREE </h5>
            </div>
        </div>

    </div>
</template>
