# [M8/Data] 링크 점검(R4)이 HTTP 상태로는 죽은 링크를 못 잡는다 — 위장 green

- **상태:** 🔵 착수 대기 · **spec-03 §4 설계 수정 필요(데이터면 — 승인 후 반영)**
- **등록:** 2026-08-15 (④ 콘텐츠 큐레이션 착수 중 실측으로 발견)
- **관련:** [spec-03 §4 R4](../specs/m7/spec-03-learning-path-links.md) · 시드 도구 `shared/scripts/concept-links-seed-to-sql.sh`

## spec 이 설계한 것

> **점검 스크립트**: `shared/scripts/check-concept-links.*` — `alive=TRUE` 전 행에
> **HTTP 상태 점검(HEAD, 실패 시 GET 폴백)** → 실패 행 `alive=FALSE`·`last_checked_at` 갱신

## 실측 — 그 방식은 작동하지 않는다

주요 provider 두 곳 모두 **존재하지 않는 경로에 200 을 반환**한다(SPA 라우팅·서버측 catch-all):

```
200  https://ko.khanacademy.org/math/this-does-not-exist-xyz-12345
200  https://ko.khanacademy.org/math/differential-calculus/bogus-unit-zzz
200  https://www.ebsmath.co.kr/bogus-path-zzz-12345
```

따라서 HTTP 상태 기반 점검은 **모든 링크가 죽어도 "전부 alive"** 를 보고한다. 이건 단순 미탐이
아니라 **위장 green** 이다 — 점검이 돌고 있다는 사실이 오히려 "링크는 살아 있다"는 잘못된 확신을 준다.
하필 R4 가 상정한 시나리오(EBS 대규모 개편)가 정확히 이 형태로 온다.

## 왜 놓쳤나

spec 작성 시점(2026-07-13)에는 링크가 0개라 **점검 대상이 없었다.** 설계는 "HTTP 200 = 살아 있음"
이라는 일반 통념을 그대로 채택했고, provider 를 실제로 때려보는 검증 단계가 없었다.
같은 성격의 함정이 이 프로젝트에서 반복된다 — 희망 상태로 판정하고 증상으로 판정하지 않는 것
(`sync-my-ip.sh` 첫 판의 tfvars 기준 비교, 외부 redis 위장 green).

## 대안 (택일 필요)

| 안 | 방식 | 한계 |
|---|---|---|
| **A. 본문 지문 검사** (권장) | 응답 본문에서 개념 관련 키워드/제목 존재를 확인. 시드 시점의 지문(길이·타이틀)을 저장해 두고 이탈을 감지 | SPA 는 본문이 빈 셸이라 칸아카데미엔 여전히 무력 |
| B. provider 별 전용 점검 | EBS·칸 각각의 API/검색 엔드포인트로 자원 존재 확인 | provider 마다 따로 짜야 하고 그쪽이 바뀌면 같이 깨진다 |
| C. 자동 점검 포기 + 사람 표본 점검 | 월 1회 사람이 N개 표본만 눈으로 확인 | 규모가 커지면 안 된다. 다만 시드 30~50 규모에선 현실적 |
| D. 링크 대상을 hub/unit 페이지로 한정 | 깊은 딥링크를 안 쓰면 깨질 확률 자체가 낮다 | 링크 유용성이 떨어진다(개념 특정성 ↓) |

**단기 권장 = C + D 조합.** 시드가 30~50개뿐이라 사람 표본 점검이 감당되고,
hub 레벨 링크는 애초에 잘 안 죽는다. 자동화는 링크 수가 늘고 provider 가 늘 때 A/B 로 간다.

## 조치

- spec-03 §4 의 "HTTP 상태 점검" 문구를 위 결정으로 교체 (**데이터면 — 사용자 승인 후 반영**)
- `check-concept-links.*` 는 **아직 구현 전** 이므로 잘못된 구현이 나가기 전에 잡은 셈이다
