<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useApi } from '@/composables/api.js';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';

const store = useStore();
const router = useRouter();
const api = useApi();

const logoUrl = computed(() => {
    return 'images/logo/logo-mmt4.png';
});

// input 데이터
const email = ref('');
const password = ref('');
const name = ref('');
const phone = ref('');
const calendar = ref('');
const calendarShow = ref('');

const isLoggedIn = ref(false);
const userDetail = ref({
    userEmail: '',
    userName: '',
    userBirthdate: '',
    userComments: ''
});
const formatDateToSlash = (dateString) => {
    const date = new Date(dateString);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${month}/${day}/${year}`;
};
onMounted(async () => {
    isLoggedIn.value = localStorage.getItem('accessToken') !== null;
    watch(
        () => store.state.accessToken,
        (newToken) => {
            isLoggedIn.value = newToken !== null;
        }
    );
    if (isLoggedIn.value) {
        try {
            const endpoint = '/api/v1/users';
            const response = await api.get(endpoint);
            userDetail.value = {
                ...response,
                userBirthdate: formatDateToSlash(response.userBirthdate)
            };
        } catch (err) {
            console.error('데이터 생성 중 에러 발생:', err);
        }
    } else {
        console.log('사용자가 로그인하지 않았습니다. 유저 정보를 건너뜁니다.');
    }
});
watch(calendar, (newVal) => {
    if (newVal) {
        calendarShow.value = formatDate(new Date(newVal));
    } else {
        calendarShow.value = '';
    }
});
// 날짜 포맷팅 함수
const formatDate = (date) => {
    const year = date.getFullYear();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    return `${year}년 ${month}월 ${day}일`;
};
const comments = ref('');
// const requestData = ref({
//     userEmail: email,
//     userPassword: password,
//     userName: name,
//     userPhone: phone,
//     userBirthdate: calendar,
//     userComments: comments
// });
// 유효성 검사
const isPasswordValid = ref(false);
const isPasswordLengthValid = ref(false);
const passwordErrorMessage = ref('8~16자, 최소한 하나의 대문자, 하나의 소문자, 하나의 숫자를 포함해야 합니다.');
const passwordLengthErrorMessage = ref('');
const validatePassword = () => {
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,16}$/;
    isPasswordValid.value = passwordRegex.test(password.value);
    // passwordErrorMessage.value = isPasswordValid.value ? '' : '8~16자의 영문 대/소문자, 숫자, 특수문자를 사용해 주세요.';
    isPasswordLengthValid.value = password.value.length <= 16;
    passwordLengthErrorMessage.value = isPasswordLengthValid.value ? '' : '16자 이하로 가능합니다.';
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
// 비밀번호와 비밀번호 확인 일치하는지 확인
const passwordConfirm = ref('');
const passwordConfirmMessage = ref('');
const isPasswordMatch = ref(false);
const confirmPassword = () => {
    if(password.value !== passwordConfirm.value && passwordConfirm.value!==''){
        passwordConfirmMessage.value = '비밀번호가 일치하지 않습니다.';
        isPasswordMatch.value = false;
    }
    else {
        passwordConfirmMessage.value = '';
        isPasswordMatch.value = true;
    }
}
// 비밀번호 변경 시 비밀번호 확인창 초기화
watch(password, () => {
    passwordConfirm.value = '';
});
// 현재 비밀번호 확인
const currentPassword = ref('');
const currentPasswordValidateMessage = ref('');
const isCurrentPasswordValid = ref(false);
const requestData2 = ref({
    userEmail: '',
    userPassword: currentPassword,
});
// watch를 사용하여 userDetail이 업데이트될 때 requestData2를 업데이트
watch(
    () => userDetail.value.userEmail,
    (newEmail) => {
        requestData2.value.userEmail = newEmail;
    }
);
const validateCurrentPassword = async () => {
    console.log(requestData2.value.userEmail);
    console.log(requestData2.value.userPassword);
    try {
        const response = await api.post('/api/v1/auth/validation', requestData2.value);
        isCurrentPasswordValid.value = response;
        if (isCurrentPasswordValid.value) {
            currentPasswordValidateMessage.value = '현재 비밀번호가 확인되었습니다.';
        } else {
            currentPasswordValidateMessage.value = '현재 비밀번호가 틀렸습니다. 다시 입력해 주세요.';
        }
    } catch (err) {
        currentPasswordValidateMessage.value = '입력하신 내용을 다시 확인해주세요.';
        console.error('데이터 생성 중 에러 발생:', err);
    }
};
// 회원 정보 수정
const requestData3 = ref({
    userPassword: password,
    userName: name,
    userBirthdate: calendar,
    userComments: comments
});
// 회원 정보 수정
const updateProfile = async () => {
    try {
        const response = await api.post('/api/v1/auth/signup', requestData3.value);
        router.push({ name: 'home' });
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
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
// 수정 정보 확인 창
const displayConfirmation = ref(false);
const openConfirmation = () => {
    displayConfirmation.value = true;
};
const closeConfirmation = () => {
    displayConfirmation.value = false;
};
// yes 버튼 클릭 시
const yesClick = async () => {
    closeConfirmation();
    await updateProfile();
    // 수정이 완료되었습니다. 홈으로 이동하시겠습니까? 문구 띄우기
    // goToHome();
};
</script>

<template>
    <div class="flex align-items-center justify-content-center mb-7">
        <div class="surface-card py-6 px-7 sm:px-8 shadow-2 border-round">
            <!-- <div class="text-center mb-7 cursor-pointer" @click="goToHome"> 홈으로 가는 클릭 이벤트 없앰 (나중에 (페이지가 아니라) 컨펌창으로 만들 때 추가하기) -->
            <div class="text-center mb-7">
                <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                <div class="text-primary text-3xl font-medium mb-3">[ My Page ]</div>
                <div class="flex align-items-center justify-content-center mt-2">
                    회원 정보 수정이 가능한 마이페이지 입니다.
                </div>
                <div class="flex align-items-center justify-content-center mt-2">
                    안전을 위해 '<span class="font-bold"> 사용빈도가 낮은 </span>' 비밀번호를 사용해 주세요.
                </div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="email" class="text-900 text-2xl font-medium"
                        >ID 
                    <span class="text-600 text-base font-normal mx-1"> (수정 불가) </span>
                    </label>
                </div>
                <div class="flex justify-content-between mb-2">
                    <InputText id="email" type="text" :placeholder="userDetail.userEmail" class="w-full" style="padding: 1rem" v-model="email" :disabled="true"/>
                </div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="passwordConfirm" class="text-900 text-2xl font-medium">정보 수정을 위해 현재 비밀번호를 확인합니다.</label>
                </div>
                <div class="flex justify-content-between mb-2">
                    <Password id="currentPassword" placeholder="현재 비밀번호 확인" :toggleMask="true" class="w-17rem" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="currentPassword" :feedback="false" />
                    <Button v-if="!isCurrentPasswordValid" @click="validateCurrentPassword" label="현재 비밀번호 확인"></Button>
                    <Button v-else disabled="true" label="확인 완료"></Button>
                </div>
                <div class="text-red-600 text-base text-font-medium mx-2">{{ currentPasswordValidateMessage }}</div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="password" class="text-900 text-2xl font-medium"
                        >New Password
                        <span class="text-red-600 text-base text-font-medium mx-2">{{ passwordLengthErrorMessage }}</span>
                        <!-- <span class="text-red-600 text-lg text-font-medium mx-2" >{{passwordErrorMessage }}</span> -->
                    </label>
                </div>
                <Password id="password" placeholder="새 비밀번호" :toggleMask="true" class="w-full mb-2" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="password" @input="validatePassword">
                    <template #header>
                        <h6>비밀번호 안전도</h6>
                    </template>
                    <template #footer>
                        <Divider />
                        <p class="mt-2">요구사항</p>
                        <ul class="pl-2 ml-2 mt-0" style="line-height: 1.5">
                            <li>8에서 16자의 길이</li>
                            <li>하나 이상의 <span class="text-lg font-bold">대문자</span></li>
                            <li>하나 이상의 <span class="text-lg font-bold">소문자</span></li>
                            <li>하나 이상의 <span class="text-lg font-bold">숫자</span></li>
                            <li>특수문자도 사용 가능합니다.</li>
                        </ul>
                    </template>
                </Password>
                <Password id="passwordConfirm" placeholder="새 비밀번호 확인" :toggleMask="true" class="w-full" :class="{ 'p-invalid': password !== passwordConfirm }" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="passwordConfirm" @input="confirmPassword" :feedback="false" />
                <div class="text-red-600 text-base text-font-medium mx-2">{{ passwordConfirmMessage }}</div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="name" class="text-900 text-2xl font-medium"
                        >Name
                        <span class="text-red-600 text-base text-font-medium mx-2">{{ userNameErrorMessage }}</span>
                    </label>
                </div>
                <InputText id="name" type="text" :placeholder="userDetail.userName" class="w-full" style="padding: 1rem" v-model="name" @input="validateUserName" :maxlength="20" />
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="calendar" class="block text-900 text-2xl font-medium mb-2"
                        >BirthDate
                        <span class="text-600 text-base font-normal mx-2"> <span class="text-red-600 font-bold">연도를 클릭</span>하여 해당 연도를 찾아보세요. </span>
                    </label>
                </div>
                <Calendar :showIcon="true" :placeholder="userDetail.userBirthdate" inputId="calendar" class="w-full" :inputStyle="{ padding: '1rem' }" v-model="calendar">Calendar</Calendar>
            </div>
            <div class="mb-7">
                <div class="flex flex-row mb-2">
                    <label for="comments" class="block text-900 text-2xl font-medium mb-2"
                        >Comments
                        <span class="text-red-600 text-base text-font-medium mx-2">{{ userCommentsErrorMessage }}</span>
                    </label>
                </div>
                <Textarea :placeholder="userDetail.userComments" :autoResize="true" class="w-full" rows="3" v-model="comments" @input="validateUserComments" :maxlength="200" />
            </div>
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <Button v-if="!isCurrentPasswordValid" :disabled="!isCurrentPasswordValid" label="[현재 비밀번호 확인]울 해주세요." class="w-full p-3 text-xl mr-2 mb-2"></Button>
            <Button v-else-if="password==''" @click="openConfirmation" label="회원정보 수정" class="w-full p-3 text-xl mr-2 mb-2" />
            <Button v-else-if="!isPasswordValid" :disabled="!isPasswordValid" label="[새 비밀번호]가 조건을 만족하지 않습니다." class="w-full p-3 text-xl mr-2 mb-2"></Button>
            <Button v-else-if="!isPasswordMatch" :disabled="!isPasswordMatch" label="[새 비밀번호 확인]울 해주세요." class="w-full p-3 text-xl mr-2 mb-2"></Button>
            <Button v-else @click="openConfirmation" label="회원정보 수정" class="w-full p-3 text-xl mr-2 mb-2" />
            <Dialog header="수정할 정보를 확인해주세요." v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5">
                    <div class="my-2" v-if="requestData3.userPassword !== ''"> Password : 비밀번호가 수정될 예정입니다. </div> 
                    <div class="my-2" v-if="requestData3.userName !== ''">Name : {{ requestData3.userName }}</div>
                    <div class="my-2" v-if="requestData3.userBirthdate !== ''">BirthDate : {{ calendarShow }}</div>
                    <div class="my-2" v-if="requestData3.userComments !== ''">Comments : {{ requestData3.userComments }}</div>
                </div>
                <div class="text-900 text-xl font-medium mx-3">회원 정보를 수정하시겠습니까?</div>
                <template #footer>
                    <Button label="No" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="Yes" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
                </template>
            </Dialog>
        </div>
    </div>
</template>
