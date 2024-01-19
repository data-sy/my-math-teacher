<script setup>
import { ref, computed } from 'vue';
import { useApi } from '@/composables/api.js';
import { useRouter } from 'vue-router';

const router = useRouter();
const api = useApi();

const logoUrl = computed(() => {
    return 'layout/images/logo-mmt4.png';
});

// input 데이터
const value = ref('');
const errorMessage = ref('');
const email = ref('');
const password = ref('');
const name = ref('');
const phone = ref('');
const calender = ref('');
const comments = ref('');
const requestData = ref({
    userEmail: email,
    userPassword: password,
    userName: name,
    userPhone: phone,
    userBirthdate: calender,
    userComments: comments
});
// 유효성 검사
const isEmailValid = ref(false);
const emailErrorMessage = ref('영어 소문자와 숫자로 구성. 3에서 16자리의 길이');
const validateEmail = () => {
    const emailRegex = /^[a-z0-9]{3,20}$/;
    isEmailValid.value = emailRegex.test(email.value);
    emailErrorMessage.value = isEmailValid.value ? '' : '영어 소문자와 숫자로 구성. 3에서 20자리의 길이';
    isCheckDuplicate.value = false;
    checkDuplicateResult.value = '';
};
const isPasswordValid = ref(true);
const passwordErrorMessage = ref('최소한 하나의 영문 대소문자, 숫자, 특수문자 포함. 8에서 16자리의 길이');
const validatePassword = () => {
    const passwordRegex = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[$@$!%*#?&])[A-Za-z\d$@$!%*#?&]{8,16}$/;
    isPasswordValid.value = passwordRegex.test(password.value);
    passwordErrorMessage.value = isPasswordValid.value ? '' : '최소한 하나의 영문 대소문자, 숫자, 특수문자 포함. 8에서 16자리의 길이';
};
const isUserNameValid = ref(true);
const userNameErrorMessage = ref('');
const validateUserName = () => {
    isUserNameValid.value = name.value.length <= 20;
    userNameErrorMessage.value = isUserNameValid.value ? '' : '20자 이하로 가능합니다.';
};
const isUserCommentsValid = ref(true);
const userCommentsErrorMessage = ref('');
const validateUserComments = () => {
    isUserCommentsValid.value = comments.value.length <= 200;
    userCommentsErrorMessage.value = isUserCommentsValid.value ? '' : '200자 이하로 가능합니다.';
};
// 중복 확인
const isCheckDuplicate = ref(false);
const checkDuplicateResult = ref('');
const checkDuplicate = async () => {
    if (isEmailValid.value) {
        try {
            const endpoint = `/checkDuplicate?userEmail=${email.value}`;
            const response = await api.get(endpoint);
            isCheckDuplicate.value = response;
            if (isCheckDuplicate.value) {
                checkDuplicateResult.value = '이미 사용중인 아이디입니다.';
            } else {
                checkDuplicateResult.value = '사용 가능한 아이디입니다.';
            }
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    }
};

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
    <div class="flex align-items-center justify-content-center mb-7">
        <div class="surface-card py-6 px-7 sm:px-8 shadow-2 border-round">
            <!-- <div class="text-center mb-7 cursor-pointer" @click="goToHome"> 홈으로 가는 클릭 이벤트 없앰 (나중에 (페이지가 아니라) 컨펌창으로 만들 때 추가하기) -->
            <div class="text-center mb-7">
                <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                <div class="text-900 text-3xl font-medium mb-3">Welcome, MMT!</div>
                <div class="flex align-items-center justify-content-center mt-5">
                    개인 프로젝트 입니다. <br />
                    안전을 위해 사용빈도가 낮은 아이디, 비밀번호를 사용해주세요.
                </div>
            </div>
            <form v-on:submit.prevent="signup">
                <div class="mb-5">
                    <div class="flex flex-row mb-2">
                        <label for="email" class="text-900 text-2xl font-medium">ID
                            <span class="text-red-600 text-base text-font-medium mx-2" >{{ emailErrorMessage }}</span>                                
                        </label>
                    </div>
                    <div class="flex justify-content-between mb-2">
                        <InputText id="email" type="text" placeholder="아이디" class="w-17rem" style="padding: 1rem" v-model="email" @input="validateEmail" />
                        <Button @click="checkDuplicate" :disabled="!isEmailValid" label="중복확인"></Button>
                    </div>
                    <div> {{ checkDuplicateResult }}</div>
                </div>
                <div class="mb-5">
                    <div class="flex flex-row mb-2">
                        <label for="password" class="text-900 text-2xl font-medium">Password
                            <!-- <span class="text-red-600 text-lg text-font-medium mx-2" >{{passwordErrorMessage }}</span> -->
                        </label>
                    </div>
                    <Password id="password" placeholder="비밀번호" :toggleMask="true" class="w-full" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="password" @input="validatePassword">
                        <template #header>
                            <h6>Pick a password</h6>
                        </template>
                        <template #footer>
                            <Divider />
                            <p class="mt-2">제안 사항</p>
                            <ul class="pl-2 ml-2 mt-0" style="line-height: 1.5">
                                <li>최소한 하나의 영어 대소문자</li>
                                <li>최소한 하나의 숫자</li>
                                <li>최소한 하나의 특수문자</li>
                                <li>길이는 8에서 16자리</li>
                            </ul>
                        </template>
                    </Password>
                    </div>
                <div class="mb-5">
                    <div class="flex flex-row mb-2">
                        <label for="name" class="text-900 text-2xl font-medium">Name
                            <span class="text-red-600 text-base text-font-medium mx-2" >{{ userNameErrorMessage }}</span>
                        </label>
                    </div>
                    <InputText id="name" type="text" placeholder="이름" class="w-full" style="padding: 1rem" v-model="name" @input="validateUserName" :maxlength="20" />
                </div>
                <!-- <label for="phone" class="block text-900 text-xl font-medium mb-2">Phone</label>
                <InputText id="phone" type="text" placeholder="핸드폰 번호" class="w-full  mb-5" style="padding: 1rem" v-model="phone" /> -->
                <div class="mb-5">
                    <div class="flex flex-row mb-2">
                        <label for="calender" class="block text-900 text-2xl font-medium mb-2">BirthDate
                            <span class="text-600 text-base font-normal mx-2" > 연도를 클릭하여 해당 연도를 찾아보세요. </span>
                        </label>
                    </div>
                    <Calendar :showIcon="true" placeholder="생년월일" inputId="calendar" class="w-full" :inputStyle="{ padding: '1rem' }" v-model="calender">Calendar</Calendar>
                </div>
                <div class="mb-5">
                    <div class="flex flex-row mb-2">
                        <label for="comments" class="block text-900 text-2xl font-medium mb-2">Comments
                            <span class="text-red-600 text-base text-font-medium mx-2" >{{ userCommentsErrorMessage }}</span>   
                        </label>
                    </div>
                    <Textarea placeholder="적고 싶은 기타사항을 적으세요. (200자 이하)" :autoResize="true" class="w-full" rows="3" v-model="comments" @input="validateUserComments" :maxlength="200" />
                </div>
                <Button type="submit" label="회원가입" class="w-full p-3 text-xl"></Button>
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
            </form>
        </div>
    </div>
</template>

