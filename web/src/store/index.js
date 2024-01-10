import { createStore } from 'vuex';
import Cookies from 'vue-cookies';
import { useApi } from '@/composables/api.js';

const api = useApi();

// isExpired 함수 정의
const isExpired = (token) => {
  if (!token) {
    return true; // 토큰이 없으면 만료됨
  }
  
  const tokenPayload = JSON.parse(atob(token.split('.')[1])); // JWT payload 디코딩
  const expirationTime = tokenPayload.exp * 1000; // 만료 시간 (밀리초)
  const currentTime = Date.now(); // 현재 시간 (밀리초)

  return currentTime >= expirationTime;
};

// [store 데이터 설정 실시]
const store = createStore ({
  state: { // [변수들의 집합]
    userTestId: -1,
    accessToken: '',
    refreshToken: ''
  },
  getters: { // [state의 변수들을 get 호출]
    getUserTestId(state){
      return state.userTestId;
    },
    getAccessToken(state){
      return state.accessToken;
    },
    getRefreshToken(state){
      return state.refreshToken;
    }
  },
  mutations: { // [변수들을 조작하는 함수들]
    setUserTestId(state, userTestId){
      state.userTestId = userTestId;
    },
    setAccessToken(state, accessToken) {
      state.accessToken = accessToken; 
      if (accessToken){
        // 헤더에 accessToken 추가
        api.setAccessToken(accessToken);
        // api.defaults.headers.common.Authorization = `Bearer ${accessToken}`;
        // 쿠키에 accessToken 저장
        // Cookies.set('accessToken', accessToken);
        // // 쿠키에 accessToken 저장 (HttpOnly, Secure, 만료일시 설정)
        // Cookies.set('accessToken', accessToken, { 
        //   httpOnly: true, 
        //   secure: true, // HTTPS에서만 전송되도록 설정
        //   expires: 1 // 1일 후 만료
        // });
      }
    },
    setRefreshToken(state, refreshToken) {
      state.refreshToken = refreshToken; 
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
    // 토큰을 받았을 때 호출되는 액션
    async saveTokens({ commit }, { accessToken, refreshToken }) {
      // 받은 토큰을 mutation을 통해 store에 저장
      commit('setAccessToken', accessToken);
      commit('setRefreshToken', refreshToken);
      // 토큰 만료 여부를 확인하고, 토큰 갱신
      const isTokenExpired = isExpired(this.state.accessToken);
      if (isTokenExpired) {
        const requestData = ref({
          grantType: "Bearer",
          accessToken: this.state.accessToken,
          refreshToken: this.state.refreshToken
        });
        try {
          const response = await api.post('/reissue', requestData.value);
          commit('setAccessToken', response.accessToken);
          commit('setRefreshToken', response.refreshToken);
        } catch (refreshError) {
          // 토큰 갱신 실패 시 로그아웃 등의 처리
          console.error('토큰 갱신에 실패했습니다. : ', refreshError);
          dispatch('logout');
        }
      }
    },
    // 로그아웃 액션
    logout({ commit }) {
      // 토큰 삭제
      commit('setAccessToken', '');
      commit('setRefreshToken', '');
      // API 요청의 Authorization 헤더 초기화
      api.defaults.headers.common.Authorization = null;
      // // 쿠키 삭제 등 로그아웃 처리
      // Cookies.remove('accessToken');
      // Cookies.remove('refreshToken');

      // 홈화면으로 이동
      router.push({ path: '/' });
    }
  },
});

export default store;
