import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';

const router = createRouter({
        history: createWebHistory(),
    // history: createWebHistory(process.env.BASE_URL),
    // history: createWebHashHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/gogo',
                    name: 'gogo',
                    component: () => import('@/views/GoGo.vue')
                },
                {
                    path: '/login',
                    name: 'login',
                    component: () => import('@/views/Login.vue')
                },
                {
                    path: '/cyto',
                    name: 'cyto',
                    component: () => import('@/views/Cyto.vue')
                },
                {
                    path: '/download',
                    name: 'download',
                    component: () => import('@/views/Download.vue')
                },
                {
                    path: '/personal',
                    name: 'personal',
                    component: () => import('@/views/PersonalView.vue')
                },
                {
                    path: '/signup',
                    name: 'signup',
                    component: () => import('@/views/SignUpView.vue')
                },
                {
                    path: '/maincomponent',
                    name: 'maincomponent',
                    component: () => import('@/components/MainComponent.vue')
                },
                {
                    path: '/record',
                    name: 'record',
                    component: () => import('@/views/RecordView.vue')
                },
                {
                    path: '/result',
                    name: 'result',
                    component: () => import('@/views/ResultView.vue')
                },
                {
                    path: '/diagnosis',
                    name: 'diagnosis',
                    component: () => import('@/views/DiagView.vue')
                },
                {
                    path: '/concepttree',
                    name: 'concepttree',
                    component: () => import('@/views/ConceptTreeView.vue')
                },
                {
                    path: '/conceptlist',
                    name: 'conceptlist',
                    component: () => import('@/views/ConceptListView.vue')
                },
                {
                    path: '/',
                    name: 'home',
                    component: () => import('@/views/HomeView.vue')
                },
            ]
        },
    ]
});

export default router;
