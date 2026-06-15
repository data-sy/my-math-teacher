# Spec 02: 하네스 핸드오프 게이트 (Harness Handoff Gates)

**상위 마일스톤:** Milestone 4 (배포 무중단화)
**대상 레이어:** 오케스트레이션/실행 — spec-01 이 "무중단을 *어떻게 만드나*"(배포 설계)라면, 본 문서는 "그걸 *누가 실행·검증하나*"
**선행 spec:** [spec-01](spec-01-zero-downtime-deployment.md) — **단방향 의존**(본 문서가 spec-01 을 참조, 역은 아님)
**성격:** 🔄 **살아있는 문서** — 마일스톤마다 사람 개입 카운트(§6)를 추적·갱신

> 본 문서는 M4 를 하네스(에이전트/워크플로)로 구현할 때, **사람 손이 꼭 타는 지점을 계측된
> pause/resume 게이트로 명세**한다. 정적 체크리스트(🙋)가 아니라, 각 게이트가 트리거·emit·재개
> 검증·개입 카운트를 갖는 핸드오프 프로토콜이다. 목표는 *per-deploy 사람 개입을 0 으로* 수렴시키고,
> 남는 개입이 비가역 행위로만 수렴하는지 지표로 확인하는 것.

---

## 0. 왜 spec-01 과 분리하는가

- **관심사 분리가 실제로 성립.** spec-01 은 하네스 없이 사람이 손으로 실행해도 그대로 유효하다 →
  spec-02 는 spec-01 에 의존하지만 역은 아님(깨끗한 단방향 의존).
- **수명이 다름.** spec-01 은 M4 종료 시 "완료"로 굳는 문서, spec-02 는 개입 카운트를 계속 추적하는
  살아있는 문서. 굳는 문서와 사는 문서를 한 파일에 두면 git 히스토리가 엉킨다.

## 1. 링크 계약 (spec-01 과 따로 표류 방지)

> 복붙 금지 — **참조**로만 묶는다.

- §5 의 **G4 grader 는 spec-01 §4(검증 방법)의 측정 정의를 참조**한다(측정 절차·임계값을 본 문서에
  복제하지 않음).
- spec-01 **§4.1 의 결정**(① 측정 타깃은 `permitAll` 엔드포인트 ② Before/After 구성 고정 ③ sub-ms
  `/health` 단독 측정 거부)은 **G4 grader 의 입력**이다. spec-01 §4.1 이 바뀌면 G4 도 따라 바뀐다.
- spec-01 §4.4(롤백)·§2.2-5(graceful drain)은 G5 의 remediation 정의다.

---

## 2. 사람 vs 하네스 — 가역성으로 재정렬

정렬축은 "정체성/결제/시크릿"이 아니라 **되돌릴 수 있는가**다. 비가역만 진짜 사람 전용.

| 비가역 (사람 전용, 못 줄임) | 가역 (게이트 등급 강등 가능) |
|---|---|
| AWS 계정·결제수단 생성, OAuth 앱 등록·동의, DockerHub/도메인 소유, **시크릿 값 최초 발급·주입** | infra apply(EC2/RDS/EIP/SG), RDS 데이터 적재, EC2 부트스트랩, **컷오버(+flip-back)**, 모든 검증 |

### 2.1 risk-forced vs chosen 사람 게이트 (지표 오염 방지)

가역+무유저라 *위험상*으론 강등 가능한 게이트라도, 사용자가 "직접 돌려보며 겪겠다"고 한 **학습
의도**로 *선택적으로* 남길 수 있다. 둘을 반드시 구분한다 — 안 그러면 "왜 사람 게이트가 아직 있지?"가
**위험 신호인지 학습 선택인지** 지표에서 안 갈린다.

- **risk-forced**: 비가역이라 강제됨(G1·G2). 줄일 수 없음.
- **chosen**: 가역이라 강등 가능하나 학습 목적 유지(G5). 신뢰 쌓이면 제거 → 지표 개선.

---

## 3. Permission 티어 (binary 해체)

| 티어 | 범위 | M4 기본 |
|---|---|---|
| **T0 영구 차단** | billing, IAM root, **시크릿 read** | 절대 안 줌 |
| **T1 read-only** | describe EC2/RDS·로그 조회 (관측·검증용) | 기본 부여 |
| **T2 scoped deploy** | 지정 `workflow_dispatch` 호출 + 지정 컨테이너 stop/run + `nginx -s reload` **만** | 신뢰 쌓인 후 |

> T0 의 "시크릿 read"는 G2 의 누수 규칙(하네스는 `gh secret set` 을 *실행*하지 않음)과 정합한다.

---

## 4. 검수자(grader) 2층 설계 — 사람을 게이트에서 뺀 자리

사람이 per-deploy 로 "결과가 맞나"를 판정하지 않는다. 검수자를 한 번 vet 하고, 그 다음부터는 기계가
채점한다. 검수자는 **검증하는 명제가 다른 2층**이다.

| 검수자 | 검증 명제 | 시점 | 사람 몫 |
|---|---|---|---|
| **동치성 오라클** = `BfsDepthMapEquivalenceTest` (Testcontainers) | "CTE == Neo4j" (코드 정확성) | CI, 배포 *전* 종결 | 오라클 설계 **1회 vet** |
| **deploy smoke grader** | "prod RDS 에 데이터가 실렸고(R4) 그 코드가 거기서 돈다(R1)" — `concepts` 가 **non-empty 기대 shape** 반환(단순 200 아님) | 배포 직후 | **기대 shape 정의 1회 vet** + anomaly flag 시 |
| **유실 grader** | "전환 구간 가로질러 유실 0%" | 배포 중 | spec-01 §4 페어니스 설계 1회 vet |

> ⚠️ **smoke grader 는 "200"만 보면 안 된다.** 시드 미적재(R4 실패) 시 빈 그래프로도 200 이 떨어지므로,
> grader 가 `200`만 단정하면 green 을 줘서 R4 실패를 통과시킨다. 반드시 **non-empty + 기대 shape** 를
> 단정해야 하고, *그 기대 shape 가 무엇인지*는 사람이 1회 정의·vet 하는 검수자 설계 대상이다(smoke
> grader 도 검수자다 — "검수자를 먼저 검수").
>
> 정확성은 CI 에서 끝난다. 배포 시점 사람 게이트는 "떴나 + non-empty 기대 shape"로 좁아지고, 그조차
> smoke grader 가 기계 채점한다. M3 go/no-go 의 본질도 "정확한가"가 아니라 **"CTE-only 가 prod-like
> 에서 안정적으로 도는가"** → grader 가 답한다.

---

## 5. pause/resume 핸드오프 게이트 명세

각 게이트: **트리거 · emit(구조화 요청) · 재개 검증 · 개입 카운트**.

| 게이트 | tier | 트리거 | emit (구조화 요청) | 재개 검증 | 개입 |
|---|---|---|---|---|---|
| **G1 정체성/결제/소유** | 비가역 | 부트스트랩 시작 | AWS 계정·결제, (HTTPS 시) OAuth 앱, DockerHub 토큰, 도메인 준비 요청 | 토큰 유효성 ping / 계정 reachable | 1회성 (risk-forced) |
| **G2 시크릿 값 주입** | 비가역 | env/Secrets 필요 시 | `gh secret set …` **명령 템플릿** + compose 주석 템플릿(플레이스홀더). 하네스는 실행 안 함 | `gh secret list`(값 미열람) + 컨테이너 env 로드 기동 | 1회성 (risk-forced) |
| **G3 infra apply** | 가역 | IaC 작성 완료 | T2 부여 시 하네스 apply; 아니면 `terraform apply` 실행 요청 + plan diff | `plan`==empty diff · RDS 연결 · **인덱스 존재 + 대표 쿼리 `EXPLAIN` 이 복합 인덱스 사용(또는 응답시간 임계)** · swap on | 1회성 |
| **G4 기동/유실 검증** | 자동 | 배포 후 | (사람 게이트 아님) anomaly flag 시에만 emit | smoke grader green(R1·R4) + 유실 grader green(spec-01 §4) | **반복, 사람 0 목표** |
| **G5 컷오버** | 가역 | 전환 직전 | (chosen) "첫 컷오버 GO?" — 학습용 | **G4 유실 grader 가 컷오버 윈도우를 가로질러 green**(대표 엔드포인트·`http_req_failed==0`). ❌ `/health` 단독 아님 | 반복, 0~1 (chosen) |
| **G6 보안표면 리뷰** | 리뷰 | SecurityConfig `permitAll` 등 변경 | diff + "permitAll 확대" 플래그 | 사람 PR 승인 | 1회성(구현 시) |

### 5.1 flip-back 은 grader 가 트리거한다 (거짓 양성 차단)

flip-back(롤백, spec-01 §4.4)을 *언제* 당기느냐의 판정자는 **사람의 직감이 아니라 G4 grader**다.

```
G4 유실 grader RED  →  하네스가 flip-back 권고 emit  →  사람은 flip-back 을 *실행만*
```

- 사람이 "뭔가 이상한데" 직감으로 당기는 검수자-없는 개입은 **거짓 양성(멀쩡한 배포를 되돌림)**을
  유발 → 금지.
- 이렇게 묶어야 G5 의 "0~1 chosen"이 **학습 의도일 때만 1**이고, 패닉 개입으로는 카운트가 안 오른다.
- (자동 flip-back 실행 자체는 spec-01 §6 Out of scope — 본 문서는 *트리거 판정*만 grader 에 묶고
  실행은 사람으로 남긴다.)

### 5.2 G4 두 grader 의 RED 경로는 갈린다

G4 는 grader 2개를 묶지만, RED 일 때 재개 경로가 시점에 따라 다르다 — grader 출력마다 경로를 닫는다.

| grader RED | 시점 | 재개 경로 |
|---|---|---|
| **smoke grader RED** | 컷오버 *전* (전환 안 함) | spec-01 §4.4 "전환 전 헬스 실패 → fragment 미변경, 구버전 유지(영향 0)" = **abort no-op**. flip-back 불필요 |
| **유실 grader RED** | 컷오버 *후* (이미 전환) | §5.1 = **flip-back 권고 emit → 사람 실행** |

---

## 6. 개입 카운트 — 하네스 성숙도 지표

| 분류 | 게이트 | M4 목표 |
|---|---|---|
| **1회성 (setup)** | G1·G2·G3·G6 | 비가역(G1·G2)만 불가피, 나머지는 IaC/리뷰로 최소화 |
| **반복 (per-deploy)** | G4(0 목표)·G5(0~1 chosen) | **per-deploy 사람 개입 0** |

- 성숙의 정의 = per-deploy 개입을 0 으로. 신뢰 쌓이면 G5 chosen GO 도 제거.
- **risk-forced 개입(§2.1)이 줄어드는가**를 마일스톤 간 비교한다. chosen 개입이 남는 건 정상(학습),
  risk-forced 가 안 줄면 그게 성숙 정체 신호.

### 6.1 개입 원장 (append-only — 첫 가동 때 채움)

> 지표 *정의*만으론 비교가 안 된다. 마일스톤마다 실제 개입 횟수를 여기 누적 기록한다. risk-forced 와
> chosen 을 분리 기록해야 §2.1 의 "위험 신호 vs 학습 선택" 구분이 데이터로 남는다.

| 마일스톤 | setup 개입 (G1·G2·G3·G6) | per-deploy 개입 (G4·G5) | risk-forced / chosen | 비고 |
|---|---|---|---|---|
| M4 | _TBD (첫 프로비저닝 시)_ | _TBD (첫 배포 시)_ | _TBD_ | 기준선 |
| _(다음 마일스톤)_ | | | | M4 대비 감소? |

---

## 7. spec-01 의존성

- spec-01 의 **R2(HTTPS)** = "후속 유지" 확정(2026-06-15). HTTPS 추진 시 G1 에 OAuth 앱 등록이
  추가되는 것 외엔 본 명세 불변.
