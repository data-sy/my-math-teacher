import axios from 'axios';

export function useApi() {
  // 로컬 dev(vite)는 프론트 5173·백엔드 8080 로 분리 → localhost:8080 유지.
  // 프로덕션 빌드는 nginx 가 같은 오리진에서 SPA·/api·/oauth2 를 함께 서빙하므로
  // window.location.origin(same-origin) 사용 → https 페이지의 localhost 호출(mixed-content)
  // 방지 + refreshToken 쿠키 same-origin. (M6 spec-01 / ADR 0009)
  const baseURL = import.meta.env.PROD ? window.location.origin : 'http://localhost:8080';
  // Axios 인스턴스 생성
  const api = axios.create({
    baseURL,
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
    },
    withCredentials: true, // refreshToken을 쿠키로 주고받기 위해
  });
  // Authorization 설정
  api.interceptors.request.use(
    (config) => {
      const accessToken = localStorage.getItem('accessToken');
      // console.log(accessToken);
      if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`;
      }
      return config;
    },
    (error) => {
      return Promise.reject(error);
    }
  );
  function removeAccessToken() {
    delete api.defaults.headers.common['Authorization'];
  }
  
  // GET 요청을 보내는 함수
  async function get(endpoint) {
    try {
      const response = await api.get(endpoint);
      return response.data;
    } catch (error) {
      throw new Error(`GET ${endpoint} failed: ${error.message}`);
    }
  }

  // POST 요청을 보내는 함수
  async function post(endpoint, data) {
    try {
      const response = await api.post(endpoint, data);
      return response.data;
    } catch (error) {
      throw new Error(`POST ${endpoint} failed: ${error.message}`);
    }
  }

  // PUT 요청을 보내는 함수
  async function put(endpoint, data) {
    try {
      const response = await api.put(endpoint, data);
      return response.data;
    } catch (error) {
      throw new Error(`PUT ${endpoint} failed: ${error.message}`);
    }
  }

  // DELETE 요청을 보내는 함수
  async function del(endpoint) {
    try {
      const response = await api.delete(endpoint);
      return response.data;
    } catch (error) {
      throw new Error(`DELETE ${endpoint} failed: ${error.message}`);
    }
  }

  return {
    get,
    post,
    put,
    del,
    removeAccessToken,
  };

}
