<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useLayout } from '@/layout/composables/layout';
import { useApi } from '@/composables/api.js';
import { useLoginDialog } from '@/composables/useLoginDialog.js';

const store = useStore();
const router = useRouter();
const { onMenuToggle } = useLayout();
const api = useApi();
const { open: openLoginDialog } = useLoginDialog();

const outsideClickListener = ref(null);
const topbarMenuActive = ref(false);

onMounted(() => {
    bindOutsideClickListener();
});

onBeforeUnmount(() => {
    unbindOutsideClickListener();
});

const logoUrl = computed(() => {
    return 'images/logo/logo-mmt4.png';
});
const bindOutsideClickListener = () => {
    if (!outsideClickListener.value) {
        outsideClickListener.value = (event) => {
            if (isOutsideClicked(event)) {
                topbarMenuActive.value = false;
            }
        };
        document.addEventListener('click', outsideClickListener.value);
    }
};
const unbindOutsideClickListener = () => {
    if (outsideClickListener.value) {
        document.removeEventListener('click', outsideClickListener.value);
        outsideClickListener.value = null;
    }
};
const isOutsideClicked = (event) => {
    if (!topbarMenuActive.value) return;

    const sidebarEl = document.querySelector('.layout-topbar-menu');
    const topbarEl = document.querySelector('.layout-topbar-menu-button');

    return !(sidebarEl.isSameNode(event.target) || sidebarEl.contains(event.target) || topbarEl.isSameNode(event.target) || topbarEl.contains(event.target));
};

const isLoggedIn = ref(false);
onMounted(() => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null; // 새로고침에 대응
    watch(() => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    )
});

// 로그인/회원가입 진입은 전역 LoginDialog 로 일원화 — 아이콘 클릭 시 공유 다이얼로그를 연다.
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
        console.error('데이터 생성 중 에러 발생:', err);
    }
};
</script>

<template>
    <div class="layout-topbar">
        <router-link to="/" class="layout-topbar-logo">
            <img :src="logoUrl" alt="logo" />
            <span>My Math Teacher</span>
        </router-link>

        <button class="p-link layout-menu-button layout-topbar-button" @click="onMenuToggle()">
            <i class="pi pi-bars"></i>
        </button>

        <!--반응형 작은 화면-->
        <div class="layout-topbar-menu-button">
            <span v-if="isLoggedIn" @click="logout()" class="p-link layout-topbar-button"> 로그아웃 </span>
            <button v-else @click="onUserClick()" class="p-link layout-topbar-button">
                <i class="pi pi-user" style="font-size: 1.5rem"></i>
            </button>
        </div>
        <!--반응형 큰 화면-->
        <div class="layout-topbar-menu">
            <span v-if="isLoggedIn" @click="logout()" class="p-link layout-topbar-button"> 로그아웃 </span>
            <button v-else @click="onUserClick()" class="p-link layout-topbar-button">
                <i class="pi pi-user" style="font-size: 1.5rem"></i>
            </button>
        </div>
    </div>
</template>

<style lang="scss" scoped></style>
