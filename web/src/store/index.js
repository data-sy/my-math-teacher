import { createStore } from 'vuex';
// 리팩토링) localStorage에 저장하는 걸 쿠키에 저장하는 걸로
import Cookies from 'vue-cookies';
import { useApi } from '@/composables/api.js';
import AuthService from '@/service/AuthService';


const api = useApi();

const store = createStore ({
  state: { 
    accessToken: localStorage.getItem('accessToken') || null,
    refreshToken: localStorage.getItem('refreshToken') || null,
  },
  getters: { 
    getAccessToken(state){
      return state.accessToken;
    },
    getRefreshToken(state){
      return state.refreshToken;
    }
  },
  mutations: { 
    setAccessToken(state, accessToken) {
      state.accessToken = accessToken;
      localStorage.setItem('accessToken', accessToken);
    },
    setRefreshToken(state, refreshToken) {
      state.refreshToken = refreshToken;
      localStorage.setItem('refreshToken', refreshToken);
      // 쿠키에 refreshToken 저장
      // Cookies.set('refreshToken', refreshToken);
      // // 쿠키에 refreshToken 저장 (HttpOnly, Secure, 만료일시 설정)
      // Cookies.set('refreshToken', refreshToken, { 
      //   httpOnly: true, 
      //   secure: true, // HTTPS에서만 전송되도록 설정
      //   expires: 1 // 1일 후 만료
      // });
    }
  },
  actions: { // [비동기 처리를 하는 함수들]
    async setAccessToken(context, accessToken) {
      context.commit('setAccessToken', accessToken);
    },
    setRefreshToken(context, refreshToken) {
      context.commit('setRefreshToken', refreshToken);
    },
    async initializeStore(context, tokens) {
      await AuthService.initializeStore(context, tokens);
    },
  },
});

export default store;
