<script setup>
import { onMounted, ref, watch } from 'vue';
import { useApi } from '@/composables/api.js';

const api = useApi();
const listboxTest = ref(null);
const listboxTests = ref([]);

// 학습지 목록
onMounted(async () => {
  try {
    const endpoint = "tests/user"
    const response = await api.get(endpoint);
    listboxTests.value = response;
  } catch (error) {
    console.error('데이터 생성 중 에러 발생:', err);
  }
});
// 추가) 정오답에 따라 활성화 주고, 기록 이미 한 학습지는 분석결과 보러가기 or 재기록 버튼 (새 userTest로 다시 저장되도록)

// 학습지 미리보기
const testDetail = ref([]);
// const testId = ref(0);
watch(listboxTest, async (newValue) => {
    try {
        // testId.value = newValue.testId;
        const endpoint = `/tests/${newValue.testId}`;
        const response = await api.get(endpoint);
        testDetail.value = response.map(item => {
            return { ...item, 
                "answerCode": true };
        });
        // const modules = await import.meta.glob(`@/assets/images/items/diag/${testId.value}/*.jpg`);
        // const images = [];
        // for (const img in modules) {
        //     images.push(modules[img].default);
        // }
        // console.log(images);
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }    
});
// 답안 원문자 표현
const renderItemAnswer = (text) => {
    return text;
};
// public에 담아뒀을 때
// const imageUrlPub = 'demo/images/491001.jpg';
// 진단학습지 번호 추출
// function extractNumberFromImagePath(itemImagePath) {
//   const match = itemImagePath.match(/diag\/(\d+)/);
//   if (match) {
//     return match[1]; // 'diag/' 뒤의 숫자 반환
//   } else {
//     return null; // 숫자를 찾을 수 없을 때 null 반환
//   }
// }
const toggleValue = ref(false);

</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5> 학습지 목록 </h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="testName" />
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <h5>정오답 기록하기</h5>
                <div v-if="!listboxTest" > </div>
                <div v-else-if="listboxTest.record">
                    트루이면 안내문구와 함께 분석결과보기 링크
                </div>
                <div v-else > 
                    <DataTable :value="testDetail" rowGroupMode="subheader" groupRowsBy="representative.name" sortMode="single" sortField="representative.name" :sortOrder="1">
                        <Column field="testItemNumber" header="번호" style="min-width: 5em"></Column>                    
                        <Column field="itemAnswer" header="정답" style="min-width: 5em">
                            <template #body="rowData">
                                <span v-html="renderItemAnswer(rowData.data.itemAnswer)"></span>
                            </template>
                        </Column>
                        <Column field="answerCode" header="정오답입력" style="min-width: 5em">
                            <template #body="rowData">
                                <ToggleButton v-model="rowData.data.answerCode" onLabel="o" offLabel="x" :style="{ width: '3.3em' }" />
                                <!-- <ToggleButton v-model="rowData.data.answerCode" :style="{ width: '3.3em' }">
                                    <template #onLabel>
                                        <i class="pi pi-check-circle" style="opacity: 0; position: absolute;"></i>
                                    </template>
                                    <template #offLabel>
                                        <i class="pi pi-minus-circle" style="opacity: 0; position: absolute;"></i>
                                    </template>      
                                </ToggleButton> -->
                            </template>
                        </Column>
                    </DataTable>
                </div>
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                <h5> 학습지 미리보기 </h5>
                <!--스크롤 기능 추가하기-->
                <div class="grid" >
                    <!-- card는 영역을 보기 위해 임시적으로 사용-->
                    <div class="card col-12" style="height: calc(8vw);"> 유저 이름, 학습지 이름, 현재 날짜, (이거는 학원 학습지 틀 참조하기 - 촬영!)</div>
                    <div v-for="(item, index) in testDetail" :key="index" class="card col-6">
                        <img :src="item.itemImagePath" alt="Item Image"  class="fit-image"/>
                        <div v-if="index > 5" style="height: calc(4vw);"> 크기 맞추기 위해 여백 만들기 </div>
                    </div>
                    <div v-for="i in (6-(testDetail.length%6))%6 " :key="'empty_' + i " style="height: calc(23vw);" class="card col-6">
                        같은 사이즈의 빈 이미지 넣기
                    </div>
                    <!-- <div>
                        src/assets에 담아뒀을 때 목록 내 모든 이미지 보기 테스트
                        <img :src="img" v-for="img of images" :key="img" class="card col-6"/>
                    </div> -->
                    <div>정답도 맘에 드는 템플릿 가져와서 사용하기(2단으로)</div>
                    <div v-for="(item, index) in testDetail" :key="index" class="col-12">
                        <!-- index+1 표시 -->
                        <span>{{ index + 1 }}. </span>                        
                        <!-- itemAnswer 표시 -->
                        <span v-html="renderItemAnswer(item.itemAnswer)"></span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style>
.fit-image {
    max-width: 100%; /* 최대 너비를 부모 요소인 div의 크기에 맞게 조정합니다. */
    height: auto; /* 이미지의 가로세로 비율을 유지하면서 조정합니다. */
    display: block; /* 인라인 요소와의 공간을 없애기 위해 블록 요소로 변경합니다. */
}
</style>
