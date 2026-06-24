<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useStore } from 'vuex';
import { useRouter, useRoute } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useLoginDialog } from '@/composables/useLoginDialog.js';

const store = useStore();
const router = useRouter();
const route = useRoute();
const api = useApi();
const { open: openLoginDialog } = useLoginDialog();

const logoUrl = computed(() => 'images/logo/logo-mmt4.png');

const go = (to) => router.push(to);

// '내 학습' 드롭다운이 묶는 하위 라우트 — 이 중 하나면 상위 항목을 active 로 본다
const MY_LEARNING_PATHS = ['/record', '/result', '/personal'];

// 상단 글로벌 내비 (spec-07): 개념 탐색 / 진단 / 내 학습 ▾(채점·결과·맞춤출제)
// command 라우팅이라 PrimeVue router-link-active 가 안 붙으므로, 현재 라우트 기준으로
// active 항목에 클래스를 주입한다(Menubar 가 item.class 를 <li> 에 반영 — Phase 3 active 하이라이트).
const navModel = computed(() => {
    const path = route.path;
    return [
        { label: '개념 탐색', icon: 'pi pi-sitemap', command: () => go('/concept'), class: path === '/concept' ? 'mmt-nav-active' : undefined },
        { label: '진단', icon: 'pi pi-file', command: () => go('/diagnosis'), class: path === '/diagnosis' ? 'mmt-nav-active' : undefined },
        {
            label: '내 학습',
            icon: 'pi pi-compass',
            class: MY_LEARNING_PATHS.includes(path) ? 'mmt-nav-active' : undefined,
            items: [
                { label: '채점하기', icon: 'pi pi-check-square', command: () => go('/record') },
                { label: 'AI 분석 결과', icon: 'pi pi-chart-line', command: () => go('/result') },
                { label: '맞춤 학습지 출제', icon: 'pi pi-book', command: () => go('/personal') }
            ]
        }
    ];
});

const isLoggedIn = ref(false);
onMounted(() => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null; // 새로고침에 대응
    watch(
        () => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    );
});

// 로그인/회원가입 진입은 전역 LoginDialog 로 일원화
const onUserClick = () => {
    openLoginDialog();
};

const logout = async () => {
    try {
        await api.del('api/v1/auth/authentication');
        store.commit('setAccessToken', null);
        localStorage.removeItem('accessToken');
        api.removeAccessToken();
        router.push({ name: 'home' });
    } catch (err) {
        console.error('로그아웃 중 에러 발생:', err);
    }
};
</script>

<template>
    <Menubar :model="navModel" class="mmt-global-nav">
        <template #start>
            <router-link to="/" class="mmt-nav-logo">
                <img :src="logoUrl" alt="My Math Teacher" />
                <span class="t-subheading mmt-brand-name">My Math Teacher</span>
            </router-link>
        </template>
        <template #end>
            <div class="flex align-items-center gap-2">
                <template v-if="isLoggedIn">
                    <Button label="회원정보 수정" icon="pi pi-user-edit" class="p-button-text" @click="go('/user-edit')" />
                    <Button label="로그아웃" icon="pi pi-sign-out" class="p-button-text" @click="logout()" />
                </template>
                <Button v-else label="로그인" icon="pi pi-user" @click="onUserClick()" />
            </div>
        </template>
    </Menubar>
</template>

<style scoped>
.mmt-global-nav {
    position: sticky;
    top: 0;
    z-index: 997;
    border-radius: 0;
}
.mmt-nav-logo {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    text-decoration: none;
    color: var(--mmt-text);
}
.mmt-nav-logo img {
    height: 2rem;
    width: auto;
}
.mmt-brand-name {
    color: var(--mmt-brand);
}

/* 로고와 내비 항목 사이 간격 (웹) — 너무 바짝 붙어 active 하단 인디케이터가 로고에 닿던 것 보정.
 * 모바일(<960px)에선 root-list 가 숨김이라 데스크톱에만 적용. */
@media screen and (min-width: 961px) {
    .mmt-global-nav :deep(.p-menubar-root-list) {
        margin-left: 1.25rem;
    }
}

/* 내비 active 라우트 하이라이트: 현재 페이지의 최상위 항목을 브랜드 컬러 + 하단 인디케이터로 표시.
 * navModel 이 active 항목에 .mmt-nav-active 를 주입 → Menubar 가 해당 <li> 에 클래스 반영. */
.mmt-global-nav :deep(.p-menuitem.mmt-nav-active > .p-menuitem-content) {
    box-shadow: inset 0 -3px 0 0 var(--mmt-brand);
}
.mmt-global-nav :deep(.p-menuitem.mmt-nav-active > .p-menuitem-content .p-menuitem-text),
.mmt-global-nav :deep(.p-menuitem.mmt-nav-active > .p-menuitem-content .p-menuitem-icon),
.mmt-global-nav :deep(.p-menuitem.mmt-nav-active > .p-menuitem-content .p-submenu-icon) {
    color: var(--mmt-brand);
}

/* Phase 3: 모바일(<960px)에선 하단 탭 바가 1차 내비 → Menubar 햄버거 폐기.
 * 상단엔 로고(start)·로그인(end)만 남긴다(내비 항목 root-list 는 모바일에서 이미 숨김). */
@media screen and (max-width: 960px) {
    .mmt-global-nav :deep(.p-menubar-button) {
        display: none;
    }
}
</style>
