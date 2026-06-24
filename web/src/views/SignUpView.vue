<script setup>
import { ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { useApi } from '@/composables/api.js';
import { useUserForm, useSubmitGuard } from '@/composables/useUserForm.js';
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

// 가입 고유 필드
const email = ref('');
const phone = ref('');
const calendar = ref('');
const calendarShow = ref('');
const maxBirthdate = new Date(); // 미래 생년월일 차단
watch(calendar, (newVal) => {
    calendarShow.value = newVal ? formatDate(new Date(newVal)) : '';
});

const requestData = ref({
    userEmail: email,
    userPassword: password,
    userName: name,
    userPhone: phone,
    userBirthdate: calendar,
    userComments: comments
});

// 아이디(이메일) 검증 — 가입 고유(중복확인 상태 리셋 포함)
const isEmailValid = ref(false);
const emailErrorMessage = ref('5~20자의 영문 소문자, 숫자만 사용 가능합니다.');
const validateEmail = () => {
    const emailRegex = /^[a-z0-9]{5,20}$/;
    isEmailValid.value = emailRegex.test(email.value);
    emailErrorMessage.value = isEmailValid.value ? '' : '5~20자의 영문 소문자, 숫자만 사용 가능합니다.';
    isNotDuplicate.value = false;
    checkDuplicateResult.value = '';
};

// 중복 확인
const isNotDuplicate = ref(false); // false = 중복이거나, 중복확인을 하지 않았거나
const checkDuplicateResult = ref('');
const checkDuplicate = async () => {
    if (isEmailValid.value) {
        try {
            const endpoint = `/api/v1/auth/duplication?userEmail=${email.value}`;
            const response = await api.get(endpoint);
            isNotDuplicate.value = !response;
            checkDuplicateResult.value = isNotDuplicate.value ? '사용 가능한 아이디입니다.' : '이미 사용중인 아이디입니다.';
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

// 제출 게이트 — 기존 disabled 체인과 동일한 통과 조건(아이디 형식·중복확인·비번 조건·비번 일치)을
// 미충족 항목 배열로 모은다. 빈 배열 = 제출 가능. 아이디 형식/중복확인은 상호배타로 한 줄만 노출.
const submitBlockers = computed(() => {
    const list = [];
    if (!isEmailValid.value) list.push({ message: '[아이디]를 5~20자 영문 소문자·숫자로 입력해 주세요.', field: 'email' });
    else if (!isNotDuplicate.value) list.push({ message: '[아이디 중복확인]을 해주세요.', field: 'email' });
    if (!isPasswordValid.value) list.push({ message: '[비밀번호]가 조건을 만족하지 않습니다.', field: 'password' });
    if (!isPasswordMatch.value) list.push({ message: '[비밀번호 확인]이 일치하지 않습니다.', field: 'passwordConfirm' });
    return list;
});
const { showBlockers, attemptSubmit, focusField } = useSubmitGuard(submitBlockers, openConfirmation);

// 회원가입
const signup = async () => {
    try {
        await api.post('/api/v1/auth/signup', requestData.value);
        router.push({ name: 'home' });
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
};
// 회원가입 성공 시 자동 로그인
const requestData2 = ref({
    userEmail: '',
    userPassword: ''
});
const login = async () => {
    requestData2.value.userEmail = requestData.value.userEmail;
    requestData2.value.userPassword = requestData.value.userPassword;
    try {
        const response = await api.post('/api/v1/auth/authentication', requestData2.value);
        store.commit('setAccessToken', response.accessToken);
    } catch (err) {
        console.error('데이터 생성 중 에러 발생:', err);
    }
};
// yes 버튼 클릭 시
const yesClick = async () => {
    closeConfirmation();
    await signup();
    await login();
    goToHome();
};
</script>

<template>
    <div class="flex align-items-center justify-content-center mb-7">
        <div class="surface-card py-6 px-7 sm:px-8 shadow-2 border-round">
            <div class="text-center mb-7">
                <img :src="logoUrl" alt="logo" class="mb-1 w-3rem flex-shrink-0" />
                <div class="t-heading mb-3">MMT에 오신 것을 환영합니다</div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="email" class="t-subheading"
                        >아이디
                        <span class="field-error mx-2">{{ emailErrorMessage }}</span>
                    </label>
                </div>
                <div class="flex justify-content-between mb-2">
                    <InputText id="email" type="text" placeholder="아이디" class="w-17rem" style="padding: 1rem" v-model="email" @input="validateEmail" />
                    <Button @click="checkDuplicate" :disabled="!isEmailValid" label="중복확인"></Button>
                </div>
                <div class="t-caption">{{ checkDuplicateResult }}</div>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="password" class="t-subheading"
                        >비밀번호
                        <span class="field-error mx-2">{{ passwordLengthErrorMessage }}</span>
                    </label>
                </div>
                <Password id="password" placeholder="비밀번호" :toggleMask="true" class="w-full" inputClass="w-full" :inputStyle="{ padding: '1rem' }" v-model="password" @input="validatePassword">
                    <template #header>
                        <h6 class="mt-0 mb-2 t-subheading">비밀번호 안전도</h6>
                    </template>
                    <template #footer>
                        <PasswordRequirements />
                    </template>
                </Password>
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="passwordConfirm" class="t-subheading">비밀번호 확인</label>
                </div>
                <Password
                    id="passwordConfirm"
                    placeholder="비밀번호 확인"
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
                <InputText id="name" type="text" placeholder="이름" class="w-full" style="padding: 1rem" v-model="name" @input="validateUserName" :maxlength="20" />
            </div>
            <div class="mb-5">
                <div class="flex flex-row mb-2">
                    <label for="calendar" class="block t-subheading mb-2"
                        >생년월일
                        <span class="t-caption mx-2">연도 칸을 누르면 <span class="font-bold">연도 목록</span>으로 빠르게 이동할 수 있습니다.</span>
                    </label>
                </div>
                <Calendar :showIcon="true" placeholder="생년월일" inputId="calendar" class="w-full" :inputStyle="{ padding: '1rem' }" :maxDate="maxBirthdate" v-model="calendar">Calendar</Calendar>
            </div>
            <div class="mb-7">
                <div class="flex flex-row mb-2">
                    <label for="comments" class="block t-subheading mb-2"
                        >기타사항
                        <span class="field-error mx-2">{{ userCommentsErrorMessage }}</span>
                    </label>
                </div>
                <Textarea placeholder="적고 싶은 기타사항을 적으세요. (200자 이하)" :autoResize="true" class="w-full" rows="3" v-model="comments" @input="validateUserComments" :maxlength="200" />
            </div>
            <ConfirmPopup></ConfirmPopup>
            <Toast />
            <div v-if="showBlockers && submitBlockers.length" class="submit-summary mb-3" role="alert">
                <div class="submit-summary__title"><i class="pi pi-exclamation-circle mr-2" />아래 항목을 확인해 주세요</div>
                <ul class="submit-summary__list">
                    <li v-for="blocker in submitBlockers" :key="blocker.field + blocker.message">
                        <a href="#" @click.prevent="focusField(blocker.field)">{{ blocker.message }}</a>
                    </li>
                </ul>
            </div>
            <Button @click="attemptSubmit" label="회원가입" class="w-full p-3 text-xl mr-2 mb-2" />
            <Dialog header="회원가입 정보를 확인해주세요." v-model:visible="displayConfirmation" :style="{ width: '350px' }" :modal="true">
                <div class="text-lg mx-3 mb-5">
                    <div class="my-2">아이디 : {{ requestData.userEmail }}</div>
                    <div class="my-2">이름 : {{ requestData.userName }}</div>
                    <div class="my-2">생년월일 : {{ calendarShow }}</div>
                    <div class="my-2">기타사항 : {{ requestData.userComments }}</div>
                </div>
                <div class="t-subheading mx-3">회원가입 하시겠습니까?</div>
                <template #footer>
                    <Button label="아니오" icon="pi pi-times" @click="closeConfirmation" class="p-button-text" />
                    <Button label="예" icon="pi pi-check" @click="yesClick" class="p-button-text" autofocus />
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
/* 시안 A — 제출 막힌 항목 요약 (필드 에러와 같은 danger 톤, 노란색 퇴출 기조) */
.submit-summary {
    border: 1px solid var(--red-200);
    background: var(--red-50);
    border-radius: 8px;
    padding: 0.75rem 1rem;
}
.submit-summary__title {
    display: flex;
    align-items: center;
    font-size: var(--mmt-fs-caption);
    font-weight: 700;
    color: var(--mmt-danger);
    margin-bottom: 0.5rem;
}
.submit-summary__list {
    margin: 0;
    padding-left: 1.25rem;
}
.submit-summary__list li {
    font-size: var(--mmt-fs-caption);
    margin: 0.25rem 0;
}
.submit-summary__list a {
    color: var(--red-700);
    text-decoration: none;
}
.submit-summary__list a:hover {
    text-decoration: underline;
}
</style>
