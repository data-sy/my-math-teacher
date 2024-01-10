<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';
import axios from 'axios';

const router = useRouter();
const store = useStore();

onMounted(() => {
    const url = window.location.href;
    const urlParams = new URLSearchParams(url.split('?')[1]);
    const token = urlParams.get('token');

    if (token) {
        const accessTokenMatch = token.match(/accessToken=([^,]+).*?,/);
        const refreshTokenMatch = token.match(/refreshToken=([^)]+)\)/);
        // console.log(token);

        if (accessTokenMatch && refreshTokenMatch) {
            const accessToken = accessTokenMatch[1];
            const refreshToken = refreshTokenMatch[1];

            // console.log(accessToken);
            // console.log(refreshToken);

            // Vuex 스토어에 accessToken과 refreshToken 저장
            store.commit('setAccessToken', accessToken);
            store.commit('setRefreshToken', refreshToken);

            // // axios의 헤더에 accessToken 추가 (vuex에서 실행 중)
            // axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
            
            router.push({ path: '/', query: {} });
            // window.location.href = 'http://localhost:5173/#/';

        } else {
            console.error('Invalid token format.');
        }
    } else {
        console.error('Token not found in URL.');
    }
});
</script>

<template></template>
