<script setup>
import { onMounted } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';

const store = useStore();
const router = useRouter();

onMounted(() => {
    const url = window.location.href;
    const urlParams = new URLSearchParams(url.split('?')[1]);
    const token = urlParams.get('token');

    if (token) {
        const accessTokenMatch = token.match(/accessToken=([^,]+).*?,/);
        const refreshTokenMatch = token.match(/refreshToken=([^)]+)\)/);

        if (accessTokenMatch && refreshTokenMatch) {
            const accessToken = accessTokenMatch[1];
            const refreshToken = refreshTokenMatch[1];

            store.commit('setAccessToken', accessToken);
            store.commit('setRefreshToken', refreshToken);

            router.push({ path: '/', query: {} });
        } else {
            console.error('Invalid token format.');
        }
    } else {
        console.error('Token not found in URL.');
    }
});
</script>

<template></template>
