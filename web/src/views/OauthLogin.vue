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
        // token 쿼리파라미터는 access 토큰 그대로다. refresh 는 HttpOnly 쿠키로 전달되므로 JS 가 다루지 않는다.
        store.commit('setAccessToken', token);
        router.push({ path: '/', query: {} });
    } else {
        console.error('Token not found in URL.');
    }
});
</script>

<template></template>
