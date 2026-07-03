<script setup>
import { ref, computed } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useLoginDialog } from '@/composables/useLoginDialog.js';

// 전역 로그인 다이얼로그 — AppTopbar 에서 추출. 토프바 아이콘·DiagView 비로그인 진입이 공유한다.
// 가시성은 useLoginDialog() 싱글톤으로 제어, AppLayout 에 1회 마운트된다.
const store = useStore();
const router = useRouter();
const api = useApi();
const { visible, close } = useLoginDialog();

const logoUrl = computed(() => 'images/logo/logo-mmt4.png');
const oauth2googlelogoUrl = computed(() => 'images/oauth2/google-logo.png');
const oauth2naverlogoUrl = computed(() => 'images/oauth2/naver-logo.png');
const oauth2kakaologoUrl = computed(() => 'images/oauth2/kakao-logo.png');

const email = ref('');
const password = ref('');
const error = ref(null);
const requestData = ref({
    userEmail: email,
    userPassword: password
});

const loginErrorMessage = ref('');
const login = async () => {
    try {
        const response = await api.post('/api/v1/auth/authentication', requestData.value);
        if (response.accessToken) {
            store.commit('setAccessToken', response.accessToken);
            error.value = null;
            close();
            router.push({ name: 'home' });
        } else {
            console.log(response);
        }
    } catch (err) {
        console.error('에러 발생:', err);
        loginErrorMessage.value = '아이디 또는 비밀번호를 잘못 입력했습니다. <br> 입력하신 내용을 다시 확인해주세요.';
        error.value = err;
    }
};

const goToSignup = () => {
    close();
    router.push({ name: 'signup' });
};
</script>

<template>
    <Dialog v-model:visible="visible" :style="{ width: '420px' }" :modal="true" :draggable="false" :dismissableMask="true" class="p-fluid">
        <div class="w-full px-3 pb-2">
            <div class="text-center mb-4">
                <img :src="logoUrl" alt="MMT" class="mb-2 w-3rem flex-shrink-0" />
                <div class="text-900 t-heading">MMT에 오신 것을 환영합니다</div>
                <div class="t-caption mt-1">로그인하고 나의 수학 학습을 이어가세요</div>
            </div>
            <form v-on:submit.prevent="login">
                <InputText id="email" v-model="email" type="text" placeholder="아이디" class="w-full mb-3" style="padding: 0.9rem 1rem" />
                <Password id="password" v-model="password" placeholder="비밀번호" :toggleMask="true" class="w-full mb-2" inputClass="w-full" :inputStyle="{ padding: '0.9rem 1rem' }" :feedback="false"></Password>
                <p v-if="loginErrorMessage" v-html="loginErrorMessage" class="text-red-600 t-caption mt-0 mb-3"></p>
                <Button type="submit" label="로그인" class="w-full p-3 border-round-xl mt-2"></Button>
            </form>
            <div class="text-center t-caption mt-3 mb-4">
                아직 계정이 없으신가요?
                <a class="signup-link ml-1" @click="goToSignup()">회원가입</a>
            </div>
            <div class="divider-container mb-4">
                <div class="left-divider"></div>
                <span class="divider-text t-caption">간편 로그인</span>
                <div class="right-divider"></div>
            </div>
            <div class="flex justify-content-center gap-4 mb-2">
                <a href="/oauth2/authorization/google" class="icon-container" aria-label="Google로 로그인">
                    <img :src="oauth2googlelogoUrl" alt="Google" class="icon" />
                </a>
                <a href="/oauth2/authorization/naver" class="icon-container" aria-label="Naver로 로그인">
                    <img :src="oauth2naverlogoUrl" alt="Naver" class="icon" />
                </a>
                <a href="/oauth2/authorization/kakao" class="icon-container kakao" aria-label="Kakao로 로그인">
                    <img :src="oauth2kakaologoUrl" alt="Kakao" class="icon" style="width: 2.4rem; height: 2.4rem" />
                </a>
            </div>
        </div>
    </Dialog>
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
    background-color: var(--mmt-border);
}
.divider-text {
    padding: 0 0.75rem;
    color: var(--mmt-text-muted);
    white-space: nowrap;
}
.signup-link {
    color: var(--mmt-brand);
    font-weight: var(--mmt-fw-semibold);
    cursor: pointer;
}
.signup-link:hover {
    text-decoration: underline;
}
.icon-container {
    width: 3rem;
    height: 3rem;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
    border: 1px solid var(--mmt-border);
    transition: box-shadow 0.15s ease, transform 0.15s ease;
}
.icon-container:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
    transform: translateY(-1px);
}
.icon {
    width: 100%;
    height: auto;
    border-radius: 50%;
}
.kakao {
    background-color: #fee500; /* 카카오 브랜드 고정색 */
    border-color: #fee500;
}
</style>
