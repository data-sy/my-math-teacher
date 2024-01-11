import { ref } from 'vue';
import { useApi } from '@/composables/api.js';

const api = useApi();

// 만료되기 1분 전으로 설정해서 자동 로그인 연장 되도록
const isExpired = (token) => {
    if (!token) {
        return true; // 토큰이 없으면 만료됨
      }
      try {
        const tokenPayload = JSON.parse(atob(token.split('.')[1])); // JWT payload 디코딩
        const expirationTime = tokenPayload.exp * 1000; // 만료 시간 (밀리초)
        const currentTime = Date.now(); // 현재 시간 (밀리초)
    
        return currentTime >= expirationTime + 60000;
      } catch (err) {
        console.error("Error decoding or parsing token:", err);
        return true; // 에러 발생 시 토큰을 만료된 것으로 처리
      }
};

// refreshToken을 사용해서 token 재생성
const reissue = async (requestData, store) => {
    try {
      const response = await api.post('/reissue', requestData.value);
      // 토큰을 store에 저장
      store.commit('setAccessToken', response.accessToken);
      store.commit('setRefreshToken', response.refreshToken);
    } catch (refreshError) {
        console.error('토큰 갱신에 실패했습니다. : ', refreshError);
        store.dispatch('logout');
    }
};

const authService = {
    async initializeStore(store) {
      const accessToken = localStorage.getItem('accessToken');
      const refreshToken = localStorage.getItem('refreshToken');
      if (accessToken) {
        store.commit('setAccessToken', accessToken);
        if (isExpired(accessToken)) {
          const requestData = {
            grantType: "Bearer",
            accessToken: accessToken,
            refreshToken: refreshToken,
          };
          await reissue(requestData, store);
        }
      }
      if (refreshToken) {
        store.commit('setRefreshToken', refreshToken);
      }
    },

    // logout({ commit, dispatch }) {
    //     // 토큰 삭제
    //     commit('setAccessToken', '');
    //     commit('setRefreshToken', '');
    //     // API 요청의 Authorization 헤더 초기화
    //     api.defaults.headers.common.Authorization = null;
    //     // // 쿠키 삭제 등 로그아웃 처리
    //     // Cookies.remove('accessToken');
    //     // Cookies.remove('refreshToken');

    //     // 홈화면으로 이동
    //     router.push({ path: '/' });
    // },
};

export default authService;
