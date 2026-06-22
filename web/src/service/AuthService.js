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

// refreshToken(HttpOnly 쿠키, withCredentials 로 자동 전송)으로 access 재발급
const reissue = async (requestData, store) => {
    try {
      const response = await api.post('/api/v1/auth/reissue', requestData);
      // 응답 body 에는 access 만 온다(refresh 는 Set-Cookie 로 회전).
      store.commit('setAccessToken', response.accessToken);
    } catch (refreshError) {
        store.commit('setAccessToken', null);
        localStorage.removeItem('accessToken');
        api.removeAccessToken();
        console.error('토큰 갱신에 실패했습니다. : ', refreshError);
    }
};

const authService = {
    async initializeStore(store) {
      const accessToken = localStorage.getItem('accessToken');
      if (accessToken) {
        store.commit('setAccessToken', accessToken);
        if (isExpired(accessToken)) {
          // refresh 는 쿠키로 자동 전송되므로 body 엔 만료 access 만 싣는다(CSRF 2차 방어용).
          const requestData = {
            grantType: "Bearer",
            accessToken: accessToken,
          };
          await reissue(requestData, store);
        }
      }
    },
};

export default authService;
