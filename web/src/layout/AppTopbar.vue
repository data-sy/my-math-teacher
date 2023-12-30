<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
// import { useLayout } from '@/layout/composables/layout';
import { useRouter } from 'vue-router';
import { useApi } from '../composables/api.js';

const api = useApi();
// const { layoutConfig, onMenuToggle } = useLayout();

// const outsideClickListener = ref(null);
// const topbarMenuActive = ref(false);
const router = useRouter();
const loginDialog = ref(false);
const submitted = ref(false);

// onMounted(() => {
//     bindOutsideClickListener();
// });

// onBeforeUnmount(() => {
//     unbindOutsideClickListener();
// });

const logoUrl = computed(() => {
    return 'layout/images/logo-mmt4.png';
    // return `layout/images/${layoutConfig.darkTheme.value ? 'logo-white' : 'logo-dark'}.svg`;
});

// const onTopBarMenuButton = () => {
//     topbarMenuActive.value = !topbarMenuActive.value;
// };
// data는 로그인 잘되는지 토큰 보는 거였으니 나중에 삭제
const data = ref(null);
const email = ref('');
const password = ref('');
const error = ref(null);
const requestData = ref({
    userEmail: email,
    userPassword: password,
});
// const clearPassword = () => {
//     const password = ref('');
// };
const closeDialog = () => {
  loginDialog.value = false;
};
const login = async () => {
  try {
    const response = await api.post('/authentication', requestData.value);
    data.value = response;
    error.value = null;
    closeDialog();
    router.push({ name: 'dashboard' }); 
  } catch (err) {
    console.error('데이터 생성 중 에러 발생:', err);
    error.value = err;
  }
};
const onUserClick = () =>{
    // 로그인 되어 있다면 유저 페이지로 router.push
    // 로그인 되어 있지 않다면 다이얼로그로 로그인 창 띄우기
    // onTopBarMenuButton();
    submitted.value = false;
    loginDialog.value = true;
    // // 다이얼로그 말고 페이지 사용 시
    // router.push('/login');
};
const checked = ref(false);
const goToSignup = () => {
  // 회원가입 페이지로 이동하는 로직 작성
  loginDialog.value = false;
  router.push({ name: 'signup' }); 
};

// const onSettingsClick = () => {
//     topbarMenuActive.value = false;
//     router.push('/documentation');
// };
// const topbarMenuClasses = computed(() => {
//     return {
//         'layout-topbar-menu-mobile-active': topbarMenuActive.value
//     };
// });

// const bindOutsideClickListener = () => {
//     if (!outsideClickListener.value) {
//         outsideClickListener.value = (event) => {
//             if (isOutsideClicked(event)) {
//                 topbarMenuActive.value = false;
//             }
//         };
//         document.addEventListener('click', outsideClickListener.value);
//     }
// };
// const unbindOutsideClickListener = () => {
//     if (outsideClickListener.value) {
//         document.removeEventListener('click', outsideClickListener);
//         outsideClickListener.value = null;
//     }
// };
// const isOutsideClicked = (event) => {
//     if (!topbarMenuActive.value) return;

//     const sidebarEl = document.querySelector('.layout-topbar-menu');
//     const topbarEl = document.querySelector('.layout-topbar-menu-button');

//     return !(sidebarEl.isSameNode(event.target) || sidebarEl.contains(event.target) || topbarEl.isSameNode(event.target) || topbarEl.contains(event.target));
// };
</script>

<template>
    <div class="layout-topbar">
        <router-link to="/" class="layout-topbar-logo">
            <img :src="logoUrl" alt="logo" />
            <span>My Math Teacher</span>
        </router-link>

        <button class="p-link layout-menu-button layout-topbar-button"> <!--@click="onMenuToggle()"-->
            <!-- <i class="pi pi-bars"></i> -->
        </button>

        <button class="p-link layout-topbar-menu-button layout-topbar-button" @click="onUserClick()">
            <i class="pi pi-user"></i>
        </button>

        <!-- <button class="p-link layout-topbar-menu-button layout-topbar-button" @click="onTopBarMenuButton()">
            <i class="pi pi-ellipsis-v"></i>
        </button>

        <div class="layout-topbar-menu" :class="topbarMenuClasses"> -->
            <!-- <button @click="onTopBarMenuButton()" class="p-link layout-topbar-button">
                <i class="pi pi-calendar"></i>
                <span>Calendar</span>
            </button> -->
            <!-- <button @click="onUserClick()" class="p-link layout-topbar-button">
                <i class="pi pi-user"></i>
                <span>Profile</span>
            </button> -->
            <!-- <button @click="onSettingsClick()" class="p-link layout-topbar-button">
                <i class="pi pi-cog"></i>
                <span>Settings</span>
            </button> -->
        <!-- </div> -->

        <Dialog v-model:visible="loginDialog" :style="{ width: '500px' }" :modal="true" class="p-fluid">
            <div style="border-radius: 56px; padding: 0.3rem; background: linear-gradient(180deg, var(--primary-color) 10%, rgba(33, 150, 243, 0) 30%)">
                <div class="w-full surface-card py-6 px-6 sm:px-8" style="border-radius: 53px">
                    <div class="text-center mb-5">
                        <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                        <div class="text-900 text-3xl font-medium mb-3">Welcome, MMT!</div>
                    </div>
                    <form v-on:submit.prevent="login">
                        <div>
                            <InputText id="email" v-model="email" type="text" placeholder="이메일" class="w-full mb-3" style="padding: 1rem" />
                            <Password id="password" v-model="password" placeholder="비밀번호" :toggleMask="true" class="w-full mb-3" inputClass="w-full" :inputStyle="{ padding: '1rem' }" :feedback="false"></Password>
                            <div class="flex align-items-center justify-content-between mb-5 gap-5">
                                <div class="flex align-items-center">
                                    <Checkbox v-model="checked" id="rememberme1" binary class="mr-2"></Checkbox>
                                    <label for="rememberme1">Remember me</label>
                                </div>
                                <a class="font-medium no-underline ml-2 text-right cursor-pointer" style="color: var(--primary-color)">Forgot password?</a>
                            </div>
                            <Button type="submit" label="Sign In" class="w-full p-3 text-xl"></Button>
                        </div>
                    </form>
                    <div class="mt-3 flex justify-center">
                        <a @click="goToSignup()" class="text-600 font-medium cursor-pointer"> 회원가입 </a>
                        <a class="text-600 font-medium cursor-pointer"> 아이디 비밀번호 찾기 </a>
                    </div>
                    <div>{{ data }}</div>
                    <div v-if="error" style="color: red">{{ error.message }}</div>
                    <div><a href="/oauth2/authorization/google">Google Login</a></div>
                    <div><a href="/oauth2/authorization/naver">Naver Login</a></div>
                    <div><a href="/oauth2/authorization/kakao">Kakao Login</a><br></div>
                </div>
            </div>
        </Dialog>
    </div>
</template>

<style lang="scss" scoped>

</style>
