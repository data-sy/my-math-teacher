<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useStore } from 'vuex';

const store = useStore();
const api = useApi();
const router = useRouter();
const loginDialog = ref(false);
const submitted = ref(false);

const logoUrl = computed(() => {
    return 'images/logo/logo-mmt4.png';
});
const oauth2googlelogoUrl = computed(() => {
    return 'images/oauth2/google-logo.png';
});
const oauth2naverlogoUrl = computed(() => {
    return 'images/oauth2/naver-logo.png';
});
const oauth2kakaologoUrl = computed(() => {
    return 'images/oauth2/kakao-logo.png';
});

const email = ref('');
const password = ref('');
const error = ref(null);
const requestData = ref({
    userEmail: email,
    userPassword: password
});

const closeDialog = () => {
    loginDialog.value = false;
};

const isLoggedIn = ref(false);
onMounted(() => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null; // 새로고침에 대응
    // console.log('mounted 후 : ', localStorage.getItem('accessToken'));
    watch(() => store.state.accessToken,
        (newToken) => {
            // console.log('마운티드 안의 워치에 들어옴 : ', isLoggedIn.value);
            isLoggedIn.value = newToken !== null;
            // console.log('마운티드 안의 watch 후 : ', isLoggedIn.value);
        }
    )
});

const loginErrorMessage = ref('');
const login = async () => {
    try {
        const response = await api.post('/api/v1/auth/authentication', requestData.value);
        if (response.accessToken){
            store.commit('setAccessToken', response.accessToken);
            store.commit('setRefreshToken', response.refreshToken);
            error.value = null;
            closeDialog();
            router.push({ name: 'home' });
        } else {
            console.log(response);
        }
    } catch (err) {
        console.error('에러 발생:', err);
        // 401 에러코드 점검하는 부분은 - 다음에 구현하기 
        // // 에러 객체의 속성들을 출력
        // for (let key in err) {
        //     console.log(key, ':', err[key]);
        // }
        // // 에러 객체의 타입 확인
        // console.error('에러 객체 타입:', typeof err);
        // console.log(JSON.stringify(err, null, 2))
        loginErrorMessage.value = '아이디 또는 비밀번호를 잘못 입력했습니다. <br> 입력하신 내용을 다시 확인해주세요.'
        error.value = err;
    }
};

const onUserClick = () => {
    submitted.value = false;
    loginDialog.value = true;
};
const checked = ref(false);
const goToSignup = () => {
    loginDialog.value = false;
    router.push({ name: 'signup' });
};

const logout = async () => {
    try {
        await api.del('api/v1/auth/authentication');
        store.commit('setAccessToken', null);
        store.commit('setRefreshToken', null);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
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

        <button class="p-link layout-menu-button layout-topbar-button"></button>

        <span v-if="isLoggedIn" @click="logout()" class="p-link layout-topbar-menu-button layout-topbar-button"> 로그<br/>아웃 </span>
        <button v-else @click="onUserClick()" class="p-link layout-topbar-menu-button layout-topbar-button">
            <i class="pi pi-user" style="font-size: 1.5rem"></i>
        </button>
        <div class="layout-topbar-menu">
            <span v-if="isLoggedIn" @click="logout()" class="p-link layout-topbar-button" > 로그<br/>아웃 </span>
            <button v-else @click="onUserClick()" class="p-link layout-topbar-button">
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
                        <InputText id="email" v-model="email" type="text" placeholder="아이디" class="w-full mb-3" style="padding: 1rem" />
                        <Password id="password" v-model="password" placeholder="비밀번호" :toggleMask="true" class="w-full mb-3" inputClass="w-full" :inputStyle="{ padding: '1rem' }" :feedback="false"></Password>
                        <div class="flex align-items-center justify-content-between mb-4 gap-5">
                            <div class="flex align-items-center">
                                <Checkbox v-model="checked" id="rememberme" binary class="mr-2"></Checkbox>
                                <label for="rememberme" class="line-through-text">Remember me</label>
                            </div>
                            <a class="font-medium no-underline ml-2 text-right cursor-pointer line-through-text" style="color: var(--primary-color)">Forgot password?</a>
                        </div>
                        <p v-html="loginErrorMessage" class="text-red-600 text-base text-font-medium"></p>
                        <Button type="submit" label="로그인" class="w-full p-2.5 text-lg border-round-2xl"></Button>
                    </div>
                </form>
                <div class="flex align-items-center justify-content-center mt-5 mb-5">
                    <div class="flex align-items-center ml-3 mr-4">
                        <a @click="goToSignup()" class="text-600 font-medium cursor-pointer"> 회원가입 </a>
                    </div>
                    <div class="vertical-line"></div>
                    <a class="text-600 font-medium cursor-pointer ml-4 line-through-text"> 아이디 비밀번호 찾기 </a>
                </div>
                <div class="divider-container mt-4 mb-4">
                    <div class="left-divider"></div>
                    <span class="divider-text"> 간편로그인 </span>
                    <div class="right-divider"></div>
                </div>
                <div class="flex justify-content-center gap-7">
                    <div class="icon-container">
                        <a href="http://localhost:8080/oauth2/authorization/google">
                            <img :src="oauth2googlelogoUrl" alt="Google" class="icon" />
                        </a>
                    </div>
                    <div class="icon-container">
                        <a href="http://localhost:8080/oauth2/authorization/naver">
                            <img :src="oauth2naverlogoUrl" alt="Naver" class="icon" />
                        </a>
                    </div>
                    <div class="icon-container kakao">
                        <a href="http://localhost:8080/oauth2/authorization/kakao">
                            <img :src="oauth2kakaologoUrl" alt="Kakao" class="icon" style="width: 2.7rem; height: 2.7rem" />
                        </a>
                    </div>
                </div>
                <div class="flex align-items-center justify-content-center mt-5 mb-5"> ( 참고 : <span class="line-through-text">취소선</span>은 아직 개발하지 않은 기능을 의미합니다.) </div>
                <!-- <div v-if="error" style="color: red">{{ error.message }}</div> -->
            </div>
        </Dialog>
    </div>
</template>

<style lang="scss" scoped>
.line-through-text {
    text-decoration: line-through !important;
}
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
    background-color: #fee500; /* Google 로고 배경색 */
}
</style>
