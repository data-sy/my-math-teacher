import { createStore } from 'vuex';

// [store 데이터 설정 실시]
const store = createStore ({
  state: { // [변수들의 집합]
    userTestId: -1
  },
  getters: { // [state의 변수들을 get 호출]
    getUserTestId(state){
      return state.userTestId;
    }
  },
  mutations: { // [변수들을 조작하는 함수들]
    setUserTestId(state, userTestId){
      state.userTestId = userTestId;
    }
  },
  actions: { // [비동기 처리를 하는 함수들]
  },
});

export default store;
