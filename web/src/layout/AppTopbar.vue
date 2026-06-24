<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useLoginDialog } from '@/composables/useLoginDialog.js';

const store = useStore();
const router = useRouter();
const api = useApi();
const { open: openLoginDialog } = useLoginDialog();

const logoUrl = computed(() => 'images/logo/logo-mmt4.png');

const go = (to) => router.push(to);

// 상단 글로벌 내비 (spec-07): 개념 탐색 / 진단 / 내 학습 ▾(채점·결과·맞춤출제)
const navModel = ref([
    { label: '개념 탐색', icon: 'pi pi-sitemap', command: () => go('/concept') },
    { label: '진단', icon: 'pi pi-file', command: () => go('/diagnosis') },
    {
        label: '내 학습',
        icon: 'pi pi-compass',
        items: [
            { label: '채점하기', icon: 'pi pi-check-square', command: () => go('/record') },
            { label: 'AI 분석 결과', icon: 'pi pi-chart-line', command: () => go('/result') },
            { label: '맞춤 학습지 출제', icon: 'pi pi-book', command: () => go('/personal') }
        ]
    }
]);

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
</style>
