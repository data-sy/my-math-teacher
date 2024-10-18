import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router';
import AppLayout from '@/layout/AppLayout.vue';

const scrollBehavior = (to, from, savedPosition) => {
    return { top: 0 };
    // return savedPosition || { top: 0 };
  };

const router = createRouter({
        history: createWebHistory(), // 해시 라우터 끔
        scrollBehavior,
    // history: createWebHistory(process.env.BASE_URL),
    // history: createWebHashHistory(),
    routes: [
        {
            path: '/',
            component: AppLayout,
            children: [
                {
                    path: '/error',
                    name: 'error',
                    component: () => import('@/views/ErrorView.vue'),
                },
                {
                    path: '/user-edit',
                    name: 'user-edit',
                    component: () => import('@/views/UserEditView.vue'),
                    beforeEnter: (to, from, next) => {
                        scrollBehavior(to, from);
                        next();
                    },
                },
                {
                    path: '/personal',
                    name: 'personal',
                    component: () => import('@/views/PersonalView.vue'),

                },
                {
                    path: '/result',
                    name: 'result',
                    component: () => import('@/views/ResultView.vue'),
                },
                {
                    path: '/record',
                    name: 'record',
                    component: () => import('@/views/RecordView.vue'),
                },
                {
                    path: '/diagnosis',
                    name: 'diagnosis',
                    component: () => import('@/views/DiagView.vue')
                },
                {
                    path: '/concept',
                    name: 'concept',
                    component: () => import('@/views/ConceptView.vue')
                },
                {
                    path: '/login',
                    name: 'login',
                    component: () => import('@/views/OauthLogin.vue'),
                },
                {
                    path: '/signup',
                    name: 'signup',
                    component: () => import('@/views/SignUpView.vue'),
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
