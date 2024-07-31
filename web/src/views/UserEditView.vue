<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';

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
const comments = ref('');

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
        return await api.put('/api/v1/users', requestData3.value);
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
        return false;
    }
};
// 회원 탈퇴 
const deleteUser = async () => {
    try {
        await api.del('/api/v1/users');
        // 탈퇴 성공, 로그아웃 진행
        store.commit('setAccessToken', null);
        store.commit('setRefreshToken', null);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        api.removeAccessToken();
        openConfirmation3();
    } catch (err) {
        console.error('회원 탈퇴 중 에러 발생:', err);
        alert('회원 탈퇴 중 오류가 발생했습니다.');
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
    const success = await updateProfile();
    // 리팩토링) 나중에 새 다이얼로그 띄우는 걸로 수정하자 (수정이 완료되었습니다. 홈으로 이동하시겠습니까?)
    if (success) goToHome();
};
// 회원 탈퇴 확인 창
const displayConfirmation2 = ref(false);
const openConfirmation2 = () => {
    displayConfirmation2.value = true;
};
const closeConfirmation2 = () => {
    displayConfirmation2.value = false;
};
// yes 버튼 클릭 시
const yesClick2 = async () => {
    closeConfirmation2();
    await deleteUser();
};
// 회원 탈퇴 후 홈 화면으로 이동
const displayConfirmation3 = ref(false);
const openConfirmation3 = () => {
    displayConfirmation3.value = true;
};
const closeConfirmation3 = () => {
    displayConfirmation3.value = false;
};
// yes 버튼 클릭 시
const yesClick3 = async () => {
    closeConfirmation3();
    goToHome();
};
// 로그인 하지 않고 [다운로드] 버튼을 누르면, 회원가입이나 로그인을 먼저 해달라고 안내
const confirmPopup = useConfirm();
const confirm = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '로그인을 먼저 해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' ',
        // accept: () => {
        //     toast.add({ severity: 'info', summary: 'Confirmed', detail: '로그인을 하면 기록할 수 있습니다.', life: 3000 });
        // }
    });
};
</script>

<template>
    <div class="grid p-fluid flex align-items-center justify-content-center">
        <!-- <div class="col-12 text-center mb-3">
            <div v-if="!isLoggedIn" class="text-orange-500 font-medium text-3xl">로그인이 필요한 페이지 입니다.</div>
        </div> -->
        <div class="surface-card py-6 px-7 sm:px-8 shadow-2 border-round mb-7">
            <!-- <div class="text-center mb-7 cursor-pointer" @click="goToHome"> 홈으로 가는 클릭 이벤트 없앰 (나중에 (페이지가 아니라) 컨펌창으로 만들 때 추가하기) -->
            <div class="text-center mb-7">
                <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                <div class="text-primary text-3xl font-medium mb-3">[ My Page ]</div>
                <div class="flex align-items-center justify-content-center mt-2">
                    회원 정보 수정이 가능한 마이페이지 입니다.
                </div>
                <!-- <div class="flex align-items-center justify-content-center mt-2">
                    안전을 위해 '<span class="font-bold"> 사용빈도가 낮은 </span>' 비밀번호를 사용해 주세요.
                </div> -->
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="email" class="text-900 text-2xl font-medium"
                        >ID 
                    <span class="text-600 text-base font-normal mx-1"> ( ID는 수정할 수 없습니다. ) </span>
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
                    <Password id="currentPassword" placeholder="현재 비밀번호 확인" :toggleMask="true" class="w-15rem" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="currentPassword" :feedback="false" />
                    <Button v-if="!isLoggedIn" @click="confirm($event)" label="현재 비밀번호 확인" class="w-11rem"></Button>
                    <Button v-else-if="!isCurrentPasswordValid" @click="validateCurrentPassword" label="현재 비밀번호 확인"></Button>
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
            <div class="mb-5">
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
            <div class="mb-7">
                <Button v-if="!isCurrentPasswordValid" :disabled="!isCurrentPasswordValid" label="[현재 비밀번호 확인]울 해주세요." class="w-full p-3 text-xl mr-2 mb-2"></Button>
                <Button v-else-if="password==''" @click="openConfirmation" label="회원정보 수정" class="w-full p-3 text-xl mr-2 mb-2" />
                <Button v-else-if="!isPasswordValid" :disabled="!isPasswordValid" label="[새 비밀번호]가 조건을 만족하지 않습니다." class="w-full p-3 text-xl mr-2 mb-2"></Button>
                <Button v-else-if="!isPasswordMatch" :disabled="!isPasswordMatch" label="[새 비밀번호 확인]울 해주세요." class="w-full p-3 text-xl mr-2 mb-2"></Button>
                <Button v-else @click="openConfirmation" label="회원정보 수정" class="w-full p-3 text-xl mr-2 mb-2" />
            </div>
            <Dialog header="수정 성공 시 홈 화면으로 이동합니다." v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5">
                    <div class="my-2" v-if="requestData3.userPassword !== ''"> Password : 비밀번호가 수정됩니다. </div> 
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
            <div class="mb-2">
                <span v-if="!isLoggedIn" @click="confirm($event)" >탈퇴 하시겠습니까?</span>
                <span v-else @click="openConfirmation2" >탈퇴 하시겠습니까?</span>
            </div>
            <Dialog header="탈퇴 시 모든 데이터가 삭제됩니다." v-model:visible="displayConfirmation2" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5">
                    <div class="my-2"> 탈퇴 시 삭제된 데이터는 <span class="text-red-600 font-bold">복구 불가</span>합니다. </div>
                    <div class="my-2"> "YES" 버튼을 누르면 돌이킬 수 없습니다. </div>
                    <!-- 나중에 추가할 기능 -->
                    <!-- <div> 다음 단어를 따라 치면 탈퇴 버튼이 활성화됩니다. </div> -->
                </div>
                <div class="text-900 text-xl font-medium mx-3">탈퇴 하시겠습니까?</div>
                <template #footer>
                    <Button label="No" icon="pi pi-times" @click="closeConfirmation2" class="p-button-text" />
                    <Button label="Yes" icon="pi pi-check" @click="yesClick2" class="p-button-text" autofocus />
                </template>
            </Dialog>
            <Dialog header="탈퇴가 완료되었습니다." v-model:visible="displayConfirmation3" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5">
                </div>
                <div class="text-900 text-xl font-medium mx-3 flex justify-content-center">
                    <div class="justify-content-center">홈 화면으로 이동합니다.</div>
                </div>
                <template #footer>
                    <Button label="OK" icon="pi pi-check" @click="yesClick3" class="p-button-text" autofocus />
                </template>
            </Dialog>
        </div>
    </div>
</template>
