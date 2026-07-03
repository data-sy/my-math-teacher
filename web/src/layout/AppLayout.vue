<script setup>
import { computed } from 'vue';
import AppTopbar from './AppTopbar.vue';
import AppLearningSteps from './AppLearningSteps.vue';
import AppBottomTabs from './AppBottomTabs.vue';
import AppFooter from './AppFooter.vue';
import LoginDialog from '@/components/LoginDialog.vue';
import { useLayout } from '@/layout/composables/layout';

const { layoutConfig } = useLayout();

const containerClass = computed(() => {
    return {
        'layout-theme-light': layoutConfig.darkTheme.value === 'light',
        'layout-theme-dark': layoutConfig.darkTheme.value === 'dark',
        'p-input-filled': layoutConfig.inputStyle.value === 'filled',
        'p-ripple-disabled': !layoutConfig.ripple.value
    };
});
</script>

<template>
    <div class="layout-wrapper mmt-shell" :class="containerClass">
        <app-topbar></app-topbar>
        <app-learning-steps></app-learning-steps>
        <div class="layout-main-container">
            <div class="layout-main">
                <router-view></router-view>
            </div>
            <app-footer></app-footer>
        </div>
        <app-bottom-tabs></app-bottom-tabs>
        <login-dialog></login-dialog>
    </div>
</template>

<style lang="scss" scoped></style>
