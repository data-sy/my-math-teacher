<script setup>
import { useLayout } from '@/layout/composables/layout';
import { ref, computed } from 'vue';
// import AppConfig from '@/layout/AppConfig.vue';
import { useApi } from '@/composables/api.js';
import { useRouter } from 'vue-router'

const router = useRouter()
const api = useApi();
const { layoutConfig } = useLayout();
// data는 회원가입 잘되는지 토큰 보는 거였으니 나중에 삭제
// const data = ref(null);
const email = ref('');
const password = ref('');
const name = ref('');
const phone = ref('');
const calender = ref('');
const comments = ref('');
// 에러 메세지도 id, 비번 다시 입력해달라는 걸로 수정
const error = ref(null);
const checked = ref(false);
const requestData = ref({
    userEmail: email,
    userPassword: password,
    userName: name,
    userPhone: phone,
    userBirthdate: calender,
    userComments: comments
});
const logoUrl = computed(() => {
    return 'layout/images/logo-mmt4.png';
});
// 홈으로
const goToHome = () => {
  try {
    router.push({ path: '/' }); 
  } catch (error) {
    console.error('에러 발생:', error);
  }
};

// 회원가입 확인 창
const displayConfirmation = ref(false);
const openConfirmation = () => {
    displayConfirmation.value = true;
};
const closeConfirmation = () => {
    displayConfirmation.value = false;
};
const signup = async () => {
    // // 보내기 전에 데이터 형태 어떻게 되는지 확인
    // console.log(requestData.value);
  try {
    const response = await api.post('/signup', requestData.value);
    router.push({ name: 'home' });
  } catch (err) {
    console.error('데이터 생성 중 에러 발생:', err);
  }
};

const yesClick = () => {
    document.querySelector('form').submit();
    closeConfirmation(); // 첫 번째 이벤트 핸들러에서 실행할 동작
    // goToHome();
//   download(); // 두 번째 이벤트 핸들러에서 실행할 동작
  // create api 추가
//   signup();
};


</script>

<template>
    <div class="surface-ground flex align-items-center justify-content-center min-h-screen min-w-screen overflow-hidden mb-7">
        <div class="flex flex-column align-items-center justify-content-center mb-3">
            <div class="w-full surface-card py-6 px-7 sm:px-8 shadow-2 border-round">
                <!-- <div class="text-center mb-7 cursor-pointer" @click="goToHome"> 홈으로 가는 클릭 이벤트 없앰 (나중에 (페이지가 아니라) 컨펌창으로 만들 때 추가하기) -->
                <div class="text-center mb-7">
                    <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                    <div class="text-900 text-3xl font-medium mb-3">Welcome, MMT!</div>
                    <div class="flex align-items-center justify-content-center mt-5"> 개인 프로젝트 입니다. <br/> 안전을 위해 사용빈도가 낮은 비밀번호를 사용해주세요. </div>

                </div>
                <form v-on:submit.prevent="signup">
                    <div>
                        <label for="email" class="block text-900 text-xl font-medium mb-2">Email</label>
                        <InputText id="email" type="text" placeholder="이메일" class="w-full mb-5" style="padding: 1rem" v-model="email" />
                        <label for="password" class="block text-900 font-medium text-xl mb-2">Password</label>
                        <Password id="password" placeholder="비밀번호" :toggleMask="true" class="w-full mb-5" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="password"></Password>
                        <label for="name" class="block text-900 text-xl font-medium mb-2">Name</label>
                        <InputText id="name" type="text" placeholder="이름" class="w-full mb-5" style="padding: 1rem" v-model="name" />
                        <label for="phone" class="block text-900 text-xl font-medium mb-2">Phone</label>
                        <InputText id="phone" type="text" placeholder="핸드폰 번호" class="w-full  mb-5" style="padding: 1rem" v-model="phone" />
                        <label for="calender" class="block text-900 text-xl font-medium mb-2">BirthDate</label>
                        <Calendar :showIcon="true" placeholder="생년월일" inputId="calendar" class="w-full mb-5" :inputStyle="{ padding: '1rem' }" v-model="calender">Calendar</Calendar>
                        <label for="comments" class="block text-900 text-xl font-medium mb-2">Comments</label>
                        <Textarea placeholder="적고 싶은 기타사항을 적으세요. (300자 이하)" :autoResize="true" class="w-full mb-5" rows="3" v-model="comments" />
                        <Button type="submit" label="Sign Up" class="w-full p-3 text-xl"></Button>
                        <!-- <Button label="Sign Up" class="w-full p-3 text-xl" @click="openConfirmation" />
                            <Dialog header="개인정보 확인" v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                                <div class="flex align-items-center justify-content-center">
                                    <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
                                    <span> 정보 확인 문구</span>
                                    <div> name : {{ name }}</div>
                                </div>
                                <template #footer>
                                    <Button label="No" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                                    <Button type="submit" label="Yes" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
                                </template>
                            </Dialog> -->
                    </div>
                </form>
                <!-- <div>{{ data }}</div> -->
                <div v-if="error" style="color: red">{{ error.message }}</div>
            </div>
        </div>
    </div>
</template>
