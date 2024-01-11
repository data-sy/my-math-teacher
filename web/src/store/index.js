import { createStore } from 'vuex';
import Cookies from 'vue-cookies';
import { useApi } from '@/composables/api.js';
import AuthService from '@/service/AuthService';

const api = useApi();

// // isExpired 함수 정의 (만료되기 1분 전으로 설정해서 자동 로그인 연장 되도록)
// const isExpired = (token) => {
//   if (!token) {
//     return true; // 토큰이 없으면 만료됨
//   }
//   try {
//     const tokenPayload = JSON.parse(atob(token.split('.')[1])); // JWT payload 디코딩
//     const expirationTime = tokenPayload.exp * 1000; // 만료 시간 (밀리초)
//     const currentTime = Date.now(); // 현재 시간 (밀리초)

//     return currentTime >= expirationTime + 60000;
//   } catch (err) {
//     console.error("Error decoding or parsing token:", err);
//     return true; // 에러 발생 시 토큰을 만료된 것으로 처리
//   }
// };

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
    async saveTokens(context, tokens) {
      await AuthService.saveTokens(context, tokens);
    },
    logout(context) {
      AuthService.logout(context);
    },
    // // 토큰을 받았을 때 호출되는 액션
    // async saveTokens({ commit }, { accessToken, refreshToken }) {
    //   // 받은 토큰을 mutation을 통해 store에 저장
    //   commit('setAccessToken', accessToken);
    //   commit('setRefreshToken', refreshToken);
    //   // 토큰 만료 여부를 확인하고, 토큰 갱신
    //   const isTokenExpired = isExpired(this.state.accessToken);
    //   if (isTokenExpired) {
    //     const requestData = ref({
    //       grantType: "Bearer",
    //       accessToken: this.state.accessToken,
    //       refreshToken: this.state.refreshToken
    //     });
    //     try {
    //       const response = await api.post('/reissue', requestData.value);
    //       commit('setAccessToken', response.accessToken);
    //       commit('setRefreshToken', response.refreshToken);
    //     } catch (refreshError) {
    //       // 토큰 갱신 실패 시 로그아웃 등의 처리
    //       console.error('토큰 갱신에 실패했습니다. : ', refreshError);
    //       dispatch('logout');
    //     }
    //   }
    // },
    // // 로그아웃 액션
    // logout({ commit }) {
    //   // 토큰 삭제
    //   commit('setAccessToken', '');
    //   commit('setRefreshToken', '');
    //   // API 요청의 Authorization 헤더 초기화
    //   api.defaults.headers.common.Authorization = null;
    //   // // 쿠키 삭제 등 로그아웃 처리
    //   // Cookies.remove('accessToken');
    //   // Cookies.remove('refreshToken');

    //   // 홈화면으로 이동
    //   router.push({ path: '/' });
    // }
  },
});

export default store;
