<template>
    <Dialog v-model:visible="loginDialog" :style="{ width: '500px' }" :modal="true" class="p-fluid">
        <div class="w-full surface-card px-6 sm:px-8">
            <div class="text-center mb-5">
                <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                <div class="text-900 text-3xl font-medium mb-3">Welcome, MMT!</div>
            </div>
            <form v-on:submit.prevent="login">
                <div>
                    <InputText id="email" v-model="email" type="text" placeholder="아이디" class="w-full mb-3" style="padding: 1rem" />
                    <Password id="password" v-model="password" placeholder="비밀번호" :toggleMask="true" class="w-full mb-4" inputClass="w-full" :inputStyle="{ padding: '1rem' }" :feedback="false"></Password>
                    <p v-html="loginErrorMessage" class="text-red-600 text-base text-font-medium"></p>
                    <Button type="submit" label="로그인" class="w-full p-2.5 p-button-raised text-lg border-round-2xl"></Button>
                </div>
            </form>
            <div class="flex align-items-center justify-content-center mt-3 mb-5">
                <Button @click="goToSignup()" label="회원가입" class="w-full p-2.5 p-button-raised p-button-secondary text-lg border-round-2xl"></Button>
            </div>
            <div class="divider-container mt-4 mb-4">
                <div class="left-divider"></div>
                <span class="divider-text"> 간편로그인 </span>
                <div class="right-divider"></div>
            </div>
            <div class="flex justify-content-center gap-7 mb-7">
                <div class="icon-container">
                    <a href="/oauth2/authorization/google">
                        <img :src="oauth2googlelogoUrl" alt="Google" class="icon" />
                    </a>
                </div>
                <div class="icon-container">
                    <a href="/oauth2/authorization/naver">
                        <img :src="oauth2naverlogoUrl" alt="Naver" class="icon" />
                    </a>
                </div>
                <div class="icon-container kakao">
                    <a href="/oauth2/authorization/kakao">
                        <img :src="oauth2kakaologoUrl" alt="Kakao" class="icon" style="width: 2.7rem; height: 2.7rem" />
                    </a>
                </div>
            </div>
        </div>
    </Dialog>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { useApi } from '@/composables/api.js';

const store = useStore();
const router = useRouter();
const api = useApi();

const loginDialog = ref(false);
const email = ref('');
const password = ref('');
const loginErrorMessage = ref('');

const logoUrl = 'images/logo/logo-mmt4.png';
const oauth2googlelogoUrl = 'images/oauth2/google-logo.png';
const oauth2naverlogoUrl = 'images/oauth2/naver-logo.png';
const oauth2kakaologoUrl = 'images/oauth2/kakao-logo.png';

const requestData = ref({
    userEmail: email,
    userPassword: password
});

const closeDialog = () => {
    loginDialog.value = false;
};

const login = async () => {
    try {
        const response = await api.post('/api/v1/auth/authentication', requestData.value);
        if (response.accessToken) {
            store.commit('setAccessToken', response.accessToken);
            store.commit('setRefreshToken', response.refreshToken);
            closeDialog();
            router.push({ name: 'home' });
        } else {
            console.log(response);
        }
    } catch (err) {
        console.error('에러 발생:', err);
        loginErrorMessage.value = '아이디 또는 비밀번호를 잘못 입력했습니다. <br> 입력하신 내용을 다시 확인해주세요.';
    }
};

const goToSignup = () => {
    loginDialog.value = false;
    router.push({ name: 'signup' });
};

export {
    loginDialog,
    email,
    password,
    loginErrorMessage,
    logoUrl,
    oauth2googlelogoUrl,
    oauth2naverlogoUrl,
    oauth2kakaologoUrl,
    login,
    goToSignup
};
</script>

<style scoped>
.divider-container {
    display: flex;
    align-items: center;
}
.left-divider,
.right-divider {
    flex-grow: 1;
    height: 1px;
    background-color: #999;
}
.divider-text {
    padding: 0 10px;
}
.icon-container {
    width: 4rem;
    height: auto;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
}
.icon {
    width: 100%;
    height: auto;
    border-radius: 50%;
}
.kakao {
    background-color: #fee500;
}
</style>
