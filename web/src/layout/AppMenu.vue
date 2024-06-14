<script setup>
import { ref } from 'vue';

import AppMenuItem from './AppMenuItem.vue';

// // 다이얼로그 표시 상태
// const isLoginDialogVisible = ref(false);

const model = ref([
    {
        label: 'Home',
        items: [{ label: '홈 화면', icon: 'pi pi-fw pi-home', to: '/' }]
    },
    {
        label: '선수 지식',
        items: [
            { label: '선수지식 알아보기', icon: 'pi pi-fw pi-sitemap', to: '/conceptlist' },
        ]
    },
    {
        label: '맞춤 학습',
        items: [
            { label: '진단 학습지 다운로드', icon: 'pi pi-fw pi-download', to: '/diagnosis' },
            { label: '채점하기', icon: 'pi pi-fw pi-check-square', to: '/record' },
            // { label: 'AI 분석 결과 보기', icon: 'pi pi-fw pi-chart-bar', to: '/result' },
            { label: 'AI 분석 결과 보기', icon: 'pi pi-fw pi-chart-line', to: '/result' },
            // { label: 'AI 분석 결과 보기', icon: 'pi pi-fw pi-chart-pie', to: '/result' },
            { label: '맞춤 학습지 출제', icon: 'pi pi-fw pi-circle', to: '/personal' }
        ]
    },
    {
        label: '마이 페이지',
        items: [
            // { label: '로그인/회원가입', icon: 'pi pi-fw pi-user', command: () => { isLoginDialogVisible.value = true }},
            { label: '회원 정보 수정', icon: 'pi pi-fw pi-user-edit', to: '/user-edit', badge: 'NEW' }
        ]
    }
]);
</script>

<template>
    <ul class="layout-menu">
        <template v-for="(item, i) in model" :key="item">
            <app-menu-item v-if="!item.separator" :item="item" :index="i"></app-menu-item>
            <li v-if="item.separator" class="menu-separator"></li>
        </template>
    </ul>


</template>

<style lang="scss" scoped></style>