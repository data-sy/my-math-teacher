import { createRouter, createWebHashHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';

const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/cyto',
                    name: 'cyto',
                    component: () => import('@/views/CytoView.vue')
                },
                {
                    path: '/signup',
                    name: 'signup',
                    component: () => import('@/views/SignUp.vue')
                },
                {
                    path: '/login',
                    name: 'login2',
                    component: () => import('@/views/LoginView.vue')
                },
                {
                    path: '/download',
                    name: 'download',
                    component: () => import('@/views/Download.vue')
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
                    path: '/diag',
                    name: 'diag',
                    component: () => import('@/views/DiagView.vue')
                },
                {
                    path: '/concepttree',
                    name: 'concepttree',
                    component: () => import('@/views/ConceptTree.vue')
                },
                {
                    path: '/conceptlist',
                    name: 'conceptlist',
                    component: () => import('@/views/ConceptList.vue')
                },
                {
                    path: '/',
                    name: 'dashboard',
                    component: () => import('@/views/Dashboard.vue')
                },
            ]
        },
    ]
});

export default router;
