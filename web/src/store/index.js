import { createStore } from 'vuex';
import { useApi } from '@/composables/api.js';
import AuthService from '@/service/AuthService';


const api = useApi();

// refreshToken 은 HttpOnly 쿠키로만 다룬다 — JS/localStorage/Vuex 에 저장하지 않는다(XSS 탈취 방지).
const store = createStore ({
  state: {
    accessToken: localStorage.getItem('accessToken') || null,
  },
  getters: {
    getAccessToken(state){
      return state.accessToken;
    },
  },
  mutations: {
    setAccessToken(state, accessToken) {
      state.accessToken = accessToken;
      localStorage.setItem('accessToken', accessToken);
    },
  },
  actions: { // [비동기 처리를 하는 함수들]
    async setAccessToken(context, accessToken) {
      context.commit('setAccessToken', accessToken);
    },
    async initializeStore(context, tokens) {
      await AuthService.initializeStore(context, tokens);
    },
  },
});

export default store;
