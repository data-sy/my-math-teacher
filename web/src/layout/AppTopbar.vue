<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';

const api = useApi();
const router = useRouter();
const loginDialog = ref(false);
const submitted = ref(false);

const logoUrl = computed(() => {
    return 'layout/images/logo-mmt4.png';
});

const email = ref('');
const password = ref('');
const error = ref(null);
const requestData = ref({
    userEmail: email,
    userPassword: password,
});

const closeDialog = () => {
  loginDialog.value = false;
};

const login = async () => {
  try {
    const response = await api.post('/authentication', requestData.value);
    error.value = null;
    closeDialog();
    router.push({ name: 'home' }); 
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

</script>

<template>
    <div class="layout-topbar">
        <router-link to="/" class="layout-topbar-logo">
            <img :src="logoUrl" alt="logo" />
            <span>My Math Teacher</span>
        </router-link>

        <button class="p-link layout-menu-button layout-topbar-button">
        </button>

        <button class="p-link layout-topbar-menu-button layout-topbar-button" @click="onUserClick()">
            <i class="pi pi-user" style="font-size: 1.5rem;"></i>
        </button>
        <div class="layout-topbar-menu">
            <button @click="onUserClick()" class="p-link layout-topbar-button">
                <i class="pi pi-user"></i>
                <span>User</span>
            </button>
        </div>

        <Dialog v-model:visible="loginDialog" :style="{ width: '500px' }" :modal="true" class="p-fluid">
            <div class="w-full surface-card px-6 sm:px-8">
                <div class="text-center mb-5">
                    <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                    <div class="text-900 text-3xl font-medium mb-3">Welcome, MMT!</div>
                </div>
                <form v-on:submit.prevent="login">
                    <div>
                        <InputText id="email" v-model="email" type="text" placeholder="이메일" class="w-full mb-3" style="padding: 1rem" />
                        <Password id="password" v-model="password" placeholder="비밀번호" :toggleMask="true" class="w-full mb-3" inputClass="w-full" :inputStyle="{ padding: '1rem' }" :feedback="false"></Password>
                        <div class="flex align-items-center justify-content-between mb-4 gap-5">
                            <div class="flex align-items-center">
                                <Checkbox v-model="checked" id="rememberme" binary class="mr-2"></Checkbox>
                                <label for="rememberme">Remember me</label>
                            </div>
                            <a class="font-medium no-underline ml-2 text-right cursor-pointer" style="color: var(--primary-color)">Forgot password?</a>
                        </div>
                        <Button type="submit" label="이메일로 로그인" class="w-full p-2.5 text-lg border-round-3xl"></Button>
                    </div>
                </form>
                <div class="flex align-items-center justify-content-center mt-5 mb-5">
                    <div class="flex align-items-center ml-3 mr-4">
                        <a @click="goToSignup()" class="text-600 font-medium cursor-pointer"> 회원가입 </a>
                    </div>
                    <div class="vertical-line"></div>
                    <a class="text-600 font-medium cursor-pointer ml-4"> 아이디 비밀번호 찾기 </a>
                </div>
                <div class="divider-container mt-4 mb-4">
                    <div class="left-divider"></div>
                    <span class="divider-text"> 간편로그인 </span>
                    <div class="right-divider"></div>
                </div>
                <div class="flex justify-content-center gap-7">
                    <div class="icon-container">
                        <a href="http://localhost:8080/oauth2/authorization/google">
                            <img src="images/oauth2/google-logo.png" alt="Google" class="icon">
                        </a>
                    </div>
                    <div class="icon-container">
                        <a href="http://localhost:8080/oauth2/authorization/naver">
                            <img src="images/oauth2/naver-logo.png" alt="Naver" class="icon">
                        </a>
                    </div>
                    <div class="icon-container kakao">
                        <a href="http://localhost:8080/oauth2/authorization/kakao">
                            <img src="images/oauth2/kakao-logo.png" alt="Kakao" class="icon" style="width: 2.7rem; height: 2.7rem;">
                        </a>
                    </div>
                </div>
                <div v-if="error" style="color: red">{{ error.message }}</div>
            </div>
        </Dialog>
    </div>
</template>

<style lang="scss" scoped>
.divider-container {
    display: flex;
    align-items: center;
}
.left-divider,
.right-divider {
    flex-grow: 1;
    height: 1px;
    background-color: #999; /* 실선의 색상을 원하는 색으로 변경 */
}
.divider-text {
    padding: 0 10px; /* 텍스트와 실선 사이의 간격 조정 */
}

.vertical-line {
    height: 1rem; /* 세로 바의 높이 */
    border-right: 1px solid #999; /* 세로 바의 스타일 및 색상 설정 */
    margin: 0 1rem; /* 세로 바 좌우 여백 설정 */
}
.icon-container {
    width: 4rem; /* 아이콘 컨테이너의 너비 설정 */
    height: auto; /* 아이콘 컨테이너의 높이 설정 */
    border-radius: 50%; /* 원형 아이콘을 위한 테두리 반지름 설정 */
    // background-color: #f0f0f0; /* 아이콘 컨테이너의 배경색 설정 */
    display: flex;
    justify-content: center;
    align-items: center;
}
.icon {
    width: 100%; /* 이미지의 크기를 부모 요소에 맞게 조절 */
    height: auto; /* 이미지의 높이를 자동으로 설정 */
    border-radius: 50%; /* 이미지를 원형으로 설정 */
    /* 다른 스타일 속성들 */
}
.kakao {
    background-color: #FEE500; /* Google 로고 배경색 */
}
</style>
