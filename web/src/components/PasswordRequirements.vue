<script setup>
// 가입/회원수정 Password 입력의 요구사항 안내 (spec-08).
// 양 폼이 복붙하던 #footer 마크업을 단일 컴포넌트로. 비번 규칙은 useUserForm.validatePassword 와 정합.
</script>

<template>
    <Divider />
    <p class="mt-2 mb-1 t-caption">요구사항</p>
    <ul class="pl-3 mt-0 mb-0 t-caption pw-req-list">
        <li>8에서 16자의 길이</li>
        <li>하나 이상의 <strong>대문자</strong></li>
        <li>하나 이상의 <strong>소문자</strong></li>
        <li>하나 이상의 <strong>숫자</strong></li>
        <li>특수문자도 사용 가능합니다.</li>
    </ul>
</template>

<style scoped>
.pw-req-list {
    line-height: 1.6;
}
</style>
