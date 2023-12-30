<script setup>
import { ref, watch } from 'vue';
const listboxTest = ref(null);
const listboxTests = ref([]);
</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12 lg:col-6 xl:col-3">
            <div class="card"> 
                <h5> 학습지 목록 </h5>
                <Listbox v-model="listboxTest" :options="listboxTests" optionLabel="name" :filter="true"/>
            </div>
        </div>
        <div class="col-12 xl:col-9">
            <div class="card">
                <h5> 분석 결과 보기 </h5>
            </div>
            <div class="card">
                <h5> 선수지식 TREE </h5>
            </div>
        </div>

    </div>
</template>
