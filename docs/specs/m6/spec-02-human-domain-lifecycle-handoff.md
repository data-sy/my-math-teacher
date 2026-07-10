# Spec 02 (👤 사람 핸드오프): 도메인·상시 계정·수명주기

**상위 마일스톤:** Milestone 6 (프로덕션 상시 배포)
**성격:** 🔄 살아있는 문서 — 사람이 손으로 수행하는 **비가역 작업 체크리스트** (M4 spec-04 패턴 재사용)
**짝 spec:** spec-01(상시 프로덕션 배포 기반 — 자동화 가능한 설계)

> ⚠️ **상태: 뼈대 — 착수 시 채움.** spec-01 §7 결정(특히 D1 스택·D2 TLS) 확정 후 각 항목의
> 구체 명령·값을 채운다. 여기 담기는 건 **어시스턴트가 대신 못 하는 비가역·사람 전용 작업**뿐이다.

---

## 왜 사람 핸드오프인가

M4 spec-02 의 정렬축(**가역성**)을 그대로 적용한다. 자동화 가능한 상시 프로비저닝·nginx·배포는
spec-01 로 두고, **비가역이거나 타 계정 권한·결제·소유가 걸린 것만** 여기로 뺀다.

---

## 체크리스트 (게이트)

### G1 — 상시 계정 결제·수명주기 수용 (비가역·판단)
- [ ] 신규 프리티어 계정 **6개월 자동 종료** 인지 + 취업 타임라인 내 수용 (백로그 §3)
- [ ] Billing → **Credits** 로 남은 크레딧·만료일 확인 (M4 소진분 반영)
- [ ] `[TODO]` 만료일 캘린더 등록 + 유료 전환 판단점 사전 결정

### G2 — 예산 알림 (가역·사람 트리거)
- [ ] AWS **Budgets** 예산/알림 생성 (크레딧 50/25/10% + 월 $ 임계) `[TODO: 임계값]`

### G3 — 도메인 DNS 연결 (비가역성 낮음이나 **타 계정 권한** 필요)
- [ ] 도메인 소유 **타 AWS 계정** Route53 호스팅 영역 접근 확보
- [ ] `www` **A 레코드 → EC2 EIP** 추가 (등록 이관 **없음** — 백로그 §4)
- [ ] 낮은 TTL 로 시작 후 `dig www.my-math-teacher.com` 로 해석 확인 `[TODO: EIP 값]`

### G4 — TLS 인증서 (사람 개입 지점)
- [ ] `[TODO: D2 결정]` Let's Encrypt(certbot) 발급 + 자동갱신 크론, 또는 ACM
- [ ] `https://www.my-math-teacher.com` 유효 인증서 확인

---

## 참조

- 상위: [`../../milestones/milestone-6-production-deploy.md`](../../milestones/milestone-6-production-deploy.md)
- 짝 spec-01: [`spec-01-always-on-production-deploy.md`](spec-01-always-on-production-deploy.md)
- 선례(M4 사람 핸드오프): [`../m4/spec-04-human-aws-provisioning-handoff.md`](../m4/spec-04-human-aws-provisioning-handoff.md)
- 비용·6개월 종료·도메인 결정 정본: [`../../backlog/production-deploy-live-resume-link.md`](../../backlog/production-deploy-live-resume-link.md)
