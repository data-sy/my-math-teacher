<script setup>
import { useLayout } from '@/layout/composables/layout';
import { computed } from 'vue';

const { layoutConfig } = useLayout();

const logoUrl = computed(() => {
    return 'images/logo/logo-mmt4.png';
});
</script>

<template>
    <footer class="layout-footer grid foot-padding">
        <div class="col-12 sm:col-6 xl:col-6 ">
            <h4>Contact Us</h4>
            <p>Email: contact.mmt.2024@gmail.com</p>
            <!-- <p>Phone: 010-0000-0000</p> -->
            <p></p>
        </div>
        <nav class="col-12 sm:col-6 xl:col-6 vertical-links">
            <h4>Social Links</h4>
            <a href="https://github.com/data-sy/my-math-teacher" target="_blank">
                Github <i class="pi pi-github"></i>
            </a>
            <a href="https://www.instagram.com/example" target="_blank">
                Notion <i class="pi pi-link"></i>
            </a>
            <!-- <a href="https://www.facebook.com/example" target="_blank">
                Blog <i class="pi pi-link"></i>
            </a> -->
        </nav>
        <div class="col-12">
            <p>&copy; Copyright 2024 MMT. All rights reserved.</p>
        </div>
    </footer>
</template>
<style lang="scss" scoped>
    .foot-padding {
        padding-left: 5rem; /* 또는 원하는 값으로 설정 */
    }
    .vertical-links a {
        display: block;
        margin-bottom: 10px; /* 링크 간 간격 조절 */
    }
</style>
