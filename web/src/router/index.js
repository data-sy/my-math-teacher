import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';

const router = createRouter({
        history: createWebHistory(), // 해시 라우터 끔
    // history: createWebHistory(process.env.BASE_URL),
    // history: createWebHashHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/user-edit',
                    name: 'user-edit',
                    component: () => import('@/views/UserEditView.vue')
                },
                {
                    path: '/login',
                    name: 'login',
                    component: () => import('@/views/OauthLogin.vue')
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
