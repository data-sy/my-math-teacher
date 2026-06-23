import { ref } from 'vue';

// 전역 로그인 다이얼로그 가시성 — 싱글톤(모듈 스코프 ref).
// 전역 토프바(AppTopbar)와 DiagView 비로그인 진입을 한 다이얼로그로 일원화한다.
// spec: docs/specs/product/spec-02-diagview-auth-entry-unification.md
const visible = ref(false);

export function useLoginDialog() {
    const open = () => {
        visible.value = true;
    };
    const close = () => {
        visible.value = false;
    };
    return { visible, open, close };
}
