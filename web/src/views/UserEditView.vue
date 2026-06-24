<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useConfirm } from 'primevue/useconfirm';
import { useApi } from '@/composables/api.js';
import { useUserForm } from '@/composables/useUserForm.js';
import PasswordRequirements from '@/components/PasswordRequirements.vue';

const store = useStore();
const router = useRouter();
const api = useApi();

const logoUrl = computed(() => {
    return 'images/logo/logo-mmt4.png';
});

// 공통 검증 (비번/이름/기타/일치 + formatDate) — spec-08
const {
    password,
    passwordConfirm,
    name,
    comments,
    isPasswordValid,
    passwordLengthErrorMessage,
    validatePassword,
    userNameErrorMessage,
    validateUserName,
    userCommentsErrorMessage,
    validateUserComments,
    passwordConfirmMessage,
    isPasswordMatch,
    confirmPassword,
    formatDate
} = useUserForm();

// 회원수정 고유 필드
const email = ref('');
const calendar = ref('');
const calendarShow = ref('');
const maxBirthdate = new Date(); // 미래 생년월일 차단

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
    calendarShow.value = newVal ? formatDate(new Date(newVal)) : '';
});

// 현재 비밀번호 확인 — 회원수정 고유
const currentPassword = ref('');
const currentPasswordValidateMessage = ref('');
const isCurrentPasswordValid = ref(false);
const requestData2 = ref({
    userEmail: '',
    userPassword: currentPassword
});
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
        currentPasswordValidateMessage.value = isCurrentPasswordValid.value ? '현재 비밀번호가 확인되었습니다.' : '현재 비밀번호가 틀렸습니다. 다시 입력해 주세요.';
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
        store.commit('setAccessToken', null);
        localStorage.removeItem('accessToken');
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
const yesClick = async () => {
    closeConfirmation();
    const success = await updateProfile();
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
const yesClick3 = async () => {
    closeConfirmation3();
    goToHome();
};
// 비로그인 상태에서 동작 시도 시 로그인 먼저 안내
const confirmPopup = useConfirm();
const confirm = (event) => {
    confirmPopup.require({
        target: event.target,
        message: '로그인을 먼저 해주세요.',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Ok',
        rejectLabel: ' '
    });
};
</script>

<template>
    <div class="grid p-fluid flex align-items-center justify-content-center">
        <div class="surface-card py-6 px-7 sm:px-8 shadow-2 border-round mb-7">
            <div class="text-center mb-7">
                <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                <div class="t-heading mb-3">마이페이지</div>
                <div class="t-caption flex align-items-center justify-content-center mt-2">회원 정보 수정이 가능한 마이페이지 입니다.</div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="email" class="t-subheading"
                        >아이디
                        <span class="t-caption mx-1">( 아이디는 수정할 수 없습니다. )</span>
                    </label>
                </div>
                <div class="flex justify-content-between mb-2">
                    <InputText id="email" type="text" :placeholder="userDetail.userEmail" class="w-full" style="padding: 1rem" v-model="email" :disabled="true" />
                </div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="passwordConfirm" class="t-subheading">정보 수정을 위해 현재 비밀번호를 확인합니다.</label>
                </div>
                <div class="flex justify-content-between mb-2">
                    <Password id="currentPassword" placeholder="현재 비밀번호 확인" :toggleMask="true" class="w-15rem" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="currentPassword" :feedback="false" />
                    <Button v-if="!isLoggedIn" @click="confirm($event)" label="현재 비밀번호 확인" class="w-11rem"></Button>
                    <Button v-else-if="!isCurrentPasswordValid" @click="validateCurrentPassword" label="현재 비밀번호 확인"></Button>
                    <Button v-else disabled="true" label="확인 완료"></Button>
                </div>
                <div class="field-error mx-2">{{ currentPasswordValidateMessage }}</div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="password" class="t-subheading"
                        >새 비밀번호
                        <span class="field-error mx-2">{{ passwordLengthErrorMessage }}</span>
                    </label>
                </div>
                <Password id="password" placeholder="새 비밀번호" :toggleMask="true" class="w-full mb-2" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="password" @input="validatePassword">
                    <template #header>
                        <h6 class="mt-0 mb-2 t-subheading">비밀번호 안전도</h6>
                    </template>
                    <template #footer>
                        <PasswordRequirements />
                    </template>
                </Password>
                <Password
                    id="passwordConfirm"
                    placeholder="새 비밀번호 확인"
                    :toggleMask="true"
                    class="w-full"
                    :class="{ 'p-invalid': password !== passwordConfirm }"
                    inputClass="w-full"
                    :inputStyle="{ padding: '1rem' }"
                    v-model="passwordConfirm"
                    @input="confirmPassword"
                    :feedback="false"
                />
                <div class="field-error mx-2">{{ passwordConfirmMessage }}</div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="name" class="t-subheading"
                        >이름
                        <span class="field-error mx-2">{{ userNameErrorMessage }}</span>
                    </label>
                </div>
                <InputText id="name" type="text" :placeholder="userDetail.userName" class="w-full" style="padding: 1rem" v-model="name" @input="validateUserName" :maxlength="20" />
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="calendar" class="block t-subheading mb-2"
                        >생년월일
                        <span class="t-caption mx-2">연도 칸을 누르면 <span class="font-bold">연도 목록</span>으로 빠르게 이동할 수 있습니다.</span>
                    </label>
                </div>
                <Calendar :showIcon="true" :placeholder="userDetail.userBirthdate" inputId="calendar" class="w-full" :inputStyle="{ padding: '1rem' }" :maxDate="maxBirthdate" v-model="calendar">Calendar</Calendar>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="comments" class="block t-subheading mb-2"
                        >기타사항
                        <span class="field-error mx-2">{{ userCommentsErrorMessage }}</span>
                    </label>
                </div>
                <Textarea :placeholder="userDetail.userComments" :autoResize="true" class="w-full" rows="3" v-model="comments" @input="validateUserComments" :maxlength="200" />
            </div>
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <div class="mb-7">
                <Button v-if="!isCurrentPasswordValid" :disabled="!isCurrentPasswordValid" label="[현재 비밀번호 확인]을 해주세요." class="w-full p-3 text-xl mr-2 mb-2"></Button>
                <Button v-else-if="password == ''" @click="openConfirmation" label="회원정보 수정" class="w-full p-3 text-xl mr-2 mb-2" />
                <Button v-else-if="!isPasswordValid" :disabled="!isPasswordValid" label="[새 비밀번호]가 조건을 만족하지 않습니다." class="w-full p-3 text-xl mr-2 mb-2"></Button>
                <Button v-else-if="!isPasswordMatch" :disabled="!isPasswordMatch" label="[새 비밀번호 확인]을 해주세요." class="w-full p-3 text-xl mr-2 mb-2"></Button>
                <Button v-else @click="openConfirmation" label="회원정보 수정" class="w-full p-3 text-xl mr-2 mb-2" />
            </div>
            <Dialog header="수정 성공 시 홈 화면으로 이동합니다." v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5">
                    <div class="my-2" v-if="requestData3.userPassword !== ''">비밀번호 : 비밀번호가 수정됩니다.</div>
                    <div class="my-2" v-if="requestData3.userName !== ''">이름 : {{ requestData3.userName }}</div>
                    <div class="my-2" v-if="requestData3.userBirthdate !== ''">생년월일 : {{ calendarShow }}</div>
                    <div class="my-2" v-if="requestData3.userComments !== ''">기타사항 : {{ requestData3.userComments }}</div>
                </div>
                <div class="t-subheading mx-3">회원 정보를 수정하시겠습니까?</div>
                <template #footer>
                    <Button label="아니오" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="예" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
                </template>
            </Dialog>
            <div class="mb-2">
                <span v-if="!isLoggedIn" @click="confirm($event)">탈퇴 하시겠습니까?</span>
                <span v-else @click="openConfirmation2">탈퇴 하시겠습니까?</span>
            </div>
            <Dialog header="탈퇴 시 모든 데이터가 삭제됩니다." v-model:visible="displayConfirmation2" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5">
                    <div class="my-2">탈퇴 시 삭제된 데이터는 <span class="text-red-600 font-bold">복구 불가</span>합니다.</div>
                    <div class="my-2">"예" 버튼을 누르면 돌이킬 수 없습니다.</div>
                </div>
                <div class="t-subheading mx-3">탈퇴 하시겠습니까?</div>
                <template #footer>
                    <Button label="아니오" icon="pi pi-times" @click="closeConfirmation2" class="p-button-text" />
                    <Button label="예" icon="pi pi-check" @click="yesClick2" class="p-button-text" autofocus />
                </template>
            </Dialog>
            <Dialog header="탈퇴가 완료되었습니다." v-model:visible="displayConfirmation3" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5"></div>
                <div class="t-subheading mx-3 flex justify-content-center">
                    <div class="justify-content-center">홈 화면으로 이동합니다.</div>
                </div>
                <template #footer>
                    <Button label="OK" icon="pi pi-check" @click="yesClick3" class="p-button-text" autofocus />
                </template>
            </Dialog>
        </div>
    </div>
</template>

<style scoped>
.field-error {
    font-size: var(--mmt-fs-caption);
    color: var(--mmt-danger);
}
</style>
