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
            <p>화면 멈춤 및 오류 발생 시 개발자에게 문의주세요.</p>
            <p>Email: contact.mmt.2024@gmail.com</p>
        </div>
        <nav class="col-12 sm:col-6 xl:col-6 vertical-links">
            <h4>Social Links</h4>
            <a href="https://github.com/data-sy/my-math-teacher" target="_blank">
                Github <i class="pi pi-github"></i>
            </a>
            <a href="https://splashy-shear-9ec.notion.site/My-Math-Teacher-11b347c3618e4a84a25908c97ae19b1d?pvs=4" target="_blank">
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
