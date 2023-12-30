<script setup>
import { onMounted, reactive, ref, watch } from 'vue';

// 다운로드 확인 창
const displayConfirmation = ref(false);
const openConfirmation = () => {
    displayConfirmation.value = true;
};
const closeConfirmation = () => {
    displayConfirmation.value = false;
};

</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between mb-3">
                    <div>
                        <span class="block text-500 font-medium mb-3"> 페이지 설명</span>
                        <div class="text-900 font-medium text-xl">여기에 설명을 적자.</div>
                        <div>
                            <i class="pi pi-github" > </i>
                            <i class="pi pi-link" > </i>
                            푸터에 링크 아이콘으로 노션 이력서 링크, 깃허브 아이콘으로 깃허브 주소 링크
                        </div>
                        <div>
                            <i class="pi pi-question-circle"></i>
                            <!-- <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i> -->
                            <i class="pi pi-user" style="font-size: 2rem"></i>
                            <i class="pi pi-user-edit" style="font-size: 2rem"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-6">
            <div class="card">
                <router-link to="/conceptlist">단위개념 목록 페이지로 이동</router-link>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-6">
            <div class="card">
                <router-link to="/diag">진단학습지 다운로드 페이지로 이동</router-link>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-6">
            <div class="card">
                <router-link to="/record">학습지 정오답 입력 페이지로 이동</router-link>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-6">
            <div class="card">
                <router-link to="/result">학습지 결과 분석 페이지로 이동</router-link>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <router-link to="/concepttree">단위개념 보기 페이지로 이동</router-link>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <router-link to="/maincomponent">store테스트 페이지로 이동</router-link>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <router-link to="/download"> 다운로드 페이지로 이동</router-link>
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                <router-link to="/cyto"> cytoscape 페이지로 이동 </router-link>
            </div>
        </div>
        
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button label="이전" class="mr-2 mb-2"></Button>
        </div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-6 xl:col-8">빈공간</div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button label="다음" class="mr-2 mb-2"></Button>
        </div>           

        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button label="이전" class="mr-2 mb-2"></Button>
        </div>
        <div class="col">빈</div>
        <div class="col">공</div>
        <div class="col">간</div>
        <div class="col">!</div>
        <div class="col-4 xs:col-4 sm:col-4 md:col-4 lg:col-3 xl:col-2">
            <Button label="다운로드" icon="pi pi-check" class="mr-2 mb-2" @click="openConfirmation" />
            <Dialog header="학습지 다운로드" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="flex align-items-center justify-content-center">
                    <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                    <span>Are you sure you want to proceed?</span>
                </div>
                <template #footer>
                    <Button label="No" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="Yes" icon="pi pi-check" @click="closeConfirmation" class="p-button-text" autofocus />
                </template>
            </Dialog>
        </div>     



        <div class="col-12">
            <div class="card">
                1 하나짜리
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-6">
            <div class="card">
                2 두개로
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-6">
            <div class="card">
                2 두개로
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                3 하나
            </div>
        </div>
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card">
                3 하나
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                3 두개로
            </div>
        </div>
        <div class="col-12 xl:col-3">
            <div class="card">
                4 하나
            </div>
        </div>
        <div class="col-12 xl:col-6">
            <div class="card">
                4 둘
            </div>
        </div>
        <div class="col-12 xl:col-3">
            <div class="card">
                4 하나
            </div>
        </div>
        <div class="col-12 xl:col-6"> <!--A-->
            <div class="col-12 lg:col-6 xl:col-3"> <!--B-->
                <div class="card">
                    div 중첩
                </div>
            </div>
        </div>
    </div>
</template>
