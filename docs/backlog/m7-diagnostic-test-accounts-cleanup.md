# [백로그] 프로덕션 진단 테스트 계정 `zdbg` 정리 (재런치 후)

- **상태:** 🔵 **착수 가능 — 트리거 조건 충족(2026-08-05 재런치 완료)** · 비차단 · 저우선
- **트리거 시점:** 프로덕션 인프라 재런치 후 → **충족됨.** 프로덕션이 라이브(`mmt-front:2.0.2`)라
  이 계정들이 실제로 살아 있다. 미처리 상태.
- **관련:** [A4 근본원인 백로그](m7-prod-auth-fresh-token-401.md)(이 계정들이 생성된 맥락) · 재런치 절차 = [`🤖-M7-티어다운-실행시퀀스.md`](../handoff/🤖-M7-티어다운-실행시퀀스.md) §재런치

## 배경

A4(프로덕션 401 트리아지, 2026-07-28) 중 **백엔드 JWT 왕복을 프론트·OAuth 없이 직접 검증**하려고 프로덕션에 임시 생성한 계정:

- `zdbg142854` (ROLE_USER)
- `zdbg2143235` (ROLE_USER)

(비밀번호 로그인 `signup → authentication → 인증필수 엔드포인트` 절차 테스트용. 실사용자 아님.)

## 현재 상태

- **2026-07-31 인프라 mothball** 로 RDS 삭제 → 두 계정 **자동 소멸**.
- 단, 보존 스냅샷 **`mmt-mothball-2026-07-31`** 에는 남아 있음 → **재런치 시 스냅샷 restore 하면 되살아난다.**

## FK 영향 조사 (2026-08-06 완료 — 스키마 정독)

**스키마 전체에 `ON DELETE CASCADE` 가 하나도 없다** → 전부 수동 자식-우선 삭제.
`users_tests.diagnosis_id` 는 **자기참조 FK**(users_tests → users_tests)라 한 번에 DELETE 하면
행 순서에 따라 실패할 수 있음 → **NULL 로 끊은 뒤 삭제**한다.

```
users ─┬─ user_authority(user_id)
       ├─ users_tests(user_id) ─┬─ answers(user_test_id) ── probabilities(answer_id)
       │   └ diagnosis_id → users_tests (자기참조)
       │                        ├─ self_report_answers(user_test_id)
       │                        └─ probabilities(user_test_id)   ← M7 additive 컬럼
       └─ learning_queues(user_id, user_test_id) ── learning_queue_items(queue_id)
```

`probabilities` 는 **`answer_id`(구 경로)와 `user_test_id`(신규 경로) 양쪽**에서 매달리므로 두 경로 다 지워야 한다.

## 실행 스크립트

[`../handoff/scripts/zdbg-cleanup.sh`](../handoff/scripts/zdbg-cleanup.sh) — RDS 가 `publicly_accessible=false` 라
맥에서 직접 못 붙으므로 **EC2 호스트 경유**(SSH). 기본 = **조사 모드(읽기 전용)**, `--delete` 로만 실제 삭제.
삭제는 단일 트랜잭션이라 부분 삭제가 없고, 삭제 전 **"타 사용자가 zdbg 데이터를 참조하나" 검사**가 0 이 아니면 중단한다.

```bash
cp ~/my-math-teacher/docs/handoff/scripts/zdbg-cleanup.sh ~/ && bash ~/zdbg-cleanup.sh
```

## 할 일 (재런치 후, 사람이 실행)

1. 재런치 검증(§재런치 §7: `login → GET /learning-queues/me` 왕복)에 **계정 하나를 재사용**하면 편하다 → 검증 끝나기 전엔 남겨둘 가치 있음.
2. 검증 완료 후 **둘 다 삭제**(실사용자 데이터에 테스트 계정이 섞이지 않게). 
   - 삭제 시 FK 참조(`users_tests`·`self_report_answers`·`learning_queues` 등) 순서 주의 — 계정에 진단 이력이 붙어 있으면 자식 행부터.
3. 이후 프로덕션 진단·검증은 **임시 계정을 그때그때 만들고 즉시 지우는** 방식 권장(장기 잔존 금지).
