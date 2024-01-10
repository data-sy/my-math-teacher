<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';
import axios from 'axios';

const router = useRouter();
const store = useStore();

onMounted(() => {
    const url = window.location.href;
});
</script>

<template>
    
</template>
