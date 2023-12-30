<script setup>
import { useLayout } from '@/layout/composables/layout';
import { ref, computed } from 'vue';
// import AppConfig from '@/layout/AppConfig.vue';
import { useApi } from '@/composables/api.js';

const api = useApi();
const { layoutConfig } = useLayout();
// data는 로그인 잘되는지 토큰 보는 거였으니 나중에 삭제
// const data = ref(null);
const email = ref('');
const password = ref('');
// 에러 메세지도 id, 비번 다시 입력해달라는 걸로 수정
const error = ref(null);
const checked = ref(false);
const requestData = ref({
    userEmail: email,
    userPassword: password,
});

const logoUrl = computed(() => {
    return 'layout/images/logo-mmt4.png';
    // return `layout/images/${layoutConfig.darkTheme.value ? 'logo-white' : 'logo-dark'}.svg`;
});

const login = async () => {
  try {
    const response = await api.post('/authentication', requestData.value);
    data.value = response;
    error.value = null;
  } catch (err) {
    console.error('데이터 생성 중 에러 발생:', err);
    error.value = err;
  }
};

</script>

<template>
    <div class="surface-ground flex align-items-center justify-content-center min-h-screen min-w-screen overflow-hidden">
        <div class="flex flex-column align-items-center justify-content-center">
            <div style="border-radius: 56px; padding: 0.3rem; background: linear-gradient(180deg, var(--primary-color) 10%, rgba(33, 150, 243, 0) 30%)">
                <div class="w-full surface-card py-8 px-5 sm:px-8" style="border-radius: 53px">
                    <div class="text-center mb-5 ">
                            <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                            <span class="text-900 text-3xl font-medium mb-3">Welcome, MMT!</span>
                        <!-- <span class="text-600 font-medium">Sign in to continue</span> -->
                    </div>
                    <form v-on:submit.prevent="login">
                        <div>
                            <InputText id="email1" type="text" placeholder="Email address" class="w-full md:w-50rem mb-4" style="padding: 1rem" v-model="email" />
                            <Password id="password1" v-model="password" placeholder="Password" :toggleMask="true" class="w-full mb-3" inputClass="w-full" :inputStyle="{ padding: '1rem' }"></Password>
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
                    <div><a href="/oauth2/authorization/google">Google Login</a></div>
                    <div><a href="/oauth2/authorization/naver">Naver Login</a></div>
                    <div><a href="/oauth2/authorization/kakao">Kakao Login</a><br></div>
                    <div v-if="error" style="color: red">{{ error.message }}</div>
                </div>
            </div>
        </div>
    </div>
    <!-- <AppConfig simple /> -->
</template>

<style scoped>
.pi-eye {
    transform: scale(1.6);
    margin-right: 1rem;
}

.pi-eye-slash {
    transform: scale(1.6);
    margin-right: 1rem;
}
</style>
