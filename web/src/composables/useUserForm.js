import { ref, nextTick, watch } from 'vue';

/**
 * 폼 제출 가드 — disabled 버튼 체인을 "항상 활성 버튼 + 클릭 시 에러 요약" 으로 대체 (시안 A).
 * SignUpView·UserEditView 공유. 검증 게이트 로직(blockersRef)은 각 뷰가 소유하고, 여기선 표시·포커스만 담당.
 *
 * @param {import('vue').Ref<{message:string,field:string}[]>} blockersRef 미충족 항목 배열(빈 배열이면 제출 가능)
 * @param {() => void} onSubmit 게이트 통과 시 호출(예: 확인창 열기)
 */
export function useSubmitGuard(blockersRef, onSubmit) {
    const showBlockers = ref(false);

    // field = 입력 요소 id. InputText 는 id 가 input 자체, Password 는 래퍼 span 이라 내부 input 을 찾는다.
    const focusField = (field) => {
        if (!field) return;
        const root = document.getElementById(field);
        if (!root) return;
        const input = root.tagName === 'INPUT' ? root : root.querySelector('input');
        (input || root).scrollIntoView({ behavior: 'smooth', block: 'center' });
        if (input) input.focus({ preventScroll: true });
    };

    const attemptSubmit = () => {
        if (blockersRef.value.length === 0) {
            onSubmit();
            return;
        }
        showBlockers.value = true; // 요약은 blockers 가 비면 v-if 로 자동 사라짐
        nextTick(() => focusField(blockersRef.value[0].field));
    };

    return { showBlockers, attemptSubmit, focusField };
}

/**
 * 가입/회원수정 폼 공통 검증 (spec-08).
 * SignUpView·UserEditView 가 복붙하던 동일 검증 로직·공유 필드 ref 를 단일 출처로 추출.
 * 거동은 기존과 동일(정규식·메시지·확인창 리셋 watch 보존). 폼 고유 필드(email·currentPassword 등)는 각 뷰에 잔존.
 */
export function useUserForm() {
    // 공유 입력 필드
    const password = ref('');
    const passwordConfirm = ref('');
    const name = ref('');
    const comments = ref('');

    // 비밀번호 검증
    const isPasswordValid = ref(false);
    const isPasswordLengthValid = ref(false);
    const passwordLengthErrorMessage = ref('');
    const validatePassword = () => {
        const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,16}$/;
        isPasswordValid.value = passwordRegex.test(password.value);
        isPasswordLengthValid.value = password.value.length <= 16;
        passwordLengthErrorMessage.value = isPasswordLengthValid.value ? '' : '16자 이하로 가능합니다.';
    };

    // 이름 검증
    const isUserNameValid = ref(true);
    const userNameErrorMessage = ref('');
    const validateUserName = () => {
        isUserNameValid.value = name.value.length <= 20;
        userNameErrorMessage.value = isUserNameValid.value ? '' : '20자 이하로 가능합니다.';
    };

    // 기타사항 검증
    const isUserCommentsValid = ref(true);
    const userCommentsErrorMessage = ref('');
    const validateUserComments = () => {
        isUserCommentsValid.value = comments.value.length <= 200;
        userCommentsErrorMessage.value = isUserCommentsValid.value ? '' : '200자 이하로 가능합니다.';
    };

    // 비밀번호 ↔ 비밀번호 확인 일치
    const passwordConfirmMessage = ref('');
    const isPasswordMatch = ref(false);
    const confirmPassword = () => {
        if (password.value !== passwordConfirm.value && passwordConfirm.value !== '') {
            passwordConfirmMessage.value = '비밀번호가 일치하지 않습니다.';
            isPasswordMatch.value = false;
        } else {
            passwordConfirmMessage.value = '';
            isPasswordMatch.value = true;
        }
    };
    // 비밀번호 변경 시 비밀번호 확인창 초기화
    watch(password, () => {
        passwordConfirm.value = '';
    });

    // 날짜 포맷팅 (생년월일 확인창 표기)
    const formatDate = (date) => {
        const year = date.getFullYear();
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');
        return `${year}년 ${month}월 ${day}일`;
    };

    return {
        password,
        passwordConfirm,
        name,
        comments,
        isPasswordValid,
        isPasswordLengthValid,
        passwordLengthErrorMessage,
        validatePassword,
        isUserNameValid,
        userNameErrorMessage,
        validateUserName,
        isUserCommentsValid,
        userCommentsErrorMessage,
        validateUserComments,
        passwordConfirmMessage,
        isPasswordMatch,
        confirmPassword,
        formatDate
    };
}
