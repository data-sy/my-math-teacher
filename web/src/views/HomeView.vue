<script setup>
</script>

<template>
    <div class="grid p-fluid">
        <div class="col-12">
            <div class="card">
                <div class="flex justify-content-between">
                    <div>
                        <div class="text-900 font-medium text-2xl mb-3"> 수학 지식을 점검하고, 부족한 부분을 찾아주는 사이트야. 어떤 선수지식이 필요한지 바로 알려주지! </div>
                        <div class="text-900 font-medium text-2xl mb-3"> MMT와 함께 더 재미있게 수학 공부해보자구~!  </div>
                        <hr class="my-3 mx-0 border-top-1 border-none surface-border" />
                        <span class="block text-600 text-xl font-medium mb-3"> 특정 개념의 선수지식이 궁금하다면? => 선수 지식 알아보기 <span class="text-red-600 font-bold">(회원가입 없이 사용 가능합니다!)</span> </span>
                        <span class="block text-600 text-xl s font-medium mb-3"> 나에게 부족한 부분이 무엇인지 궁금하다면? => &#9312;->&#9313;->&#9314;->&#9315; <span class="text-red-600">(회원가입이 필요한 기능입니다.)</span> </span>
                        <div> 회원가입 및 로그인은 오른쪽 상단의 <i class="pi pi-user" style="font-size: 1.5rem" ></i> 아이콘 클릭</div>
                        <!-- <div>
                            <i class="pi pi-question-circle"></i> 마우스 오버/아웃으로 설명 추가
                            <i class="pi pi-user-edit" style="font-size: 2rem"></i> 로그인 후에는 user아이콘이 user-edit아이콘이 되도록
                        </div> -->
                    </div>
                </div>
            </div>
        </div>
        <div class="col-12">
            <router-link to="/conceptlist">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> 선수 지식 알아보기 </div>
                    <span class="block text-600 font-medium mb-2"> 원하는 개념의 선수지식이 어떻게 되는지 알려줄게! </span>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/diagnosis">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> &#9312; 실력 점검하기 </div>
                    <span class="block text-600 font-medium mb-2"> 중단원에 따른 진단학습지가 준비되어 있어. <br> 어떤 개념을 알고, 어떤 개념을 모르는지 확인해보자! </span>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/record">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> &#9313; 정오답 입력하기 </div>
                    <span class="block text-600 font-medium mb-2"> 학습지 채점 결과를 입력해서 AI에게 알려줘. </span>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6">
            <router-link to="/result">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> &#9314; 분석 결과보기  </div>
                    <span class="block text-600 font-medium mb-2"> &#9313; 의 정오답 기록을 바탕으로, 너에게 어떤 지식이 필요한지 AI가 알려줄거야! 문장수정</span>
                </div>
            </router-link>
        </div>
        <div class="col-12 md:col-6 xl:col-6 mb-7">
            <router-link to="/personal">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> &#9315; 맞춤 학습  </div>
                    <span class="block text-600 font-medium mb-2"> 분석 결과에 따라 너에게 필요한 문제만 쏙쏙 제공해줄게! </span>
                </div>
            </router-link>
        </div>
        <!-- <div class="col-12 md:col-6 xl:col-6 mb-7">
            <router-link to="/maincomponent">
                <div class="card">
                    <div class="text-900 font-medium text-xl mb-4"> 스토어  </div>
                </div>
            </router-link>
        </div> -->
    </div>
</template>
