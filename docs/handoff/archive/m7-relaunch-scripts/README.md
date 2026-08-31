# M7 재런치 실행 스크립트 (2026-08-05, 소진됨)

2026-07-31 mothball 로 내린 프로덕션을 다시 올리며 실제로 실행한 스크립트다.
**소진된 자산** — 재실행 목적이 아니라 *절차의 기록*으로 보존한다.
맥락·게이트 판정 결과 정본 = [`../🤖-M7-재런치-핸드오프.md`](../🤖-M7-재런치-핸드오프.md).

## 왜 스크립트인가

긴 명령을 대화 코드블록으로 넘기면 터미널 폭에서 접힌 지점이 개행으로 붙어 명령이 쪼개진다
(이 세션에서 실측: `aws rds describe-db-snapshots …` 가 4조각으로 분해, `export TF_VAR_…="$(jq …)"`
는 따옴표가 끊겨 셸이 멈춤). 그래서 **파일로 쓰고 `bash ~/xxx.sh` 한 줄만** 전달했다.
전부 항목별 ✅/❌ 와 마지막 판정 줄을 찍어, 사용자는 출력만 붙여넣으면 됐다.

## 실행 순서

| 순서 | 스크립트 | 하는 일 | 게이트 |
|---|---|---|---|
| 1 | `gate1.sh` | AWS 자격·스냅샷 available·재생성 대상 비었나 | GATE 1 |
| 2 | `gate2a.sh` | `terraform plan` 검사 (create 4·destroy 0·snapshot 배선) | GATE 2-a |
| 3 | `apply2.sh` | apply + EC2/RDS/EIP 실물 검증 | GATE 2-b |
| 4 | `gate3.sh` | DNS 전파 (권위 NS 직접 조회 + 공개 리졸버) | GATE 3 |
| 5 | `phase4a.sh` | SG `my_ip` 갱신 → SSH 열기 | Phase 4a |
| 6 | `mkenv.sh` | 로컬에서 `mmt-backend.env` 조립 (값 미출력, sha 앞 8자만) | Phase 4b |
| 7 | `phase4c.sh` | 자산 scp + `mmt-net`·redis·mmt-ai 기동 + RDS 로그인 실증 | Phase 4c |
| 7' | `remote-phase4c.sh` | 위 스크립트가 호스트로 scp 해 실행하는 호스트측 본체 | (Phase 4c) |
| 8 | `phase4d.sh` | nginx fragment 배치 + certbot standalone 발급(dry-run 선행) | Phase 4d |
| 9 | `phase5a.sh` | M7 진단 DDL 적용 (멱등, md5 대조 후 전송) | GATE 5-a |
| 10 | `phase5b.sh` | 백엔드 blue 손기동 + A4 마스킹 수정 검증 | GATE 5-b |
| 11 | `phase4e.sh` | 프론트 기동 + 외부 HTTPS 검증 | Phase 4e |
| 12 | `deployfront.sh` | 프론트 재빌드분 push + 호스트 교체 | — |

## 재사용 시 반드시 갱신할 값

하드코딩돼 있다. 다음 재런치는 인프라가 새로 생기므로 전부 달라진다.

- `HOST=15.164.145.106` (EIP) · `i-098e63bf15a150633` (인스턴스 ID)
- `mmt-mothball-2026-07-31` (스냅샷 식별자) · `112.223.174.134` (`my_ip`)
- `mmt2024/mmt-backend:ea94a1a…` · `mmt2024/mmt-front:2.0.1` (이미지 태그)
- RDS 엔드포인트 (이번엔 우연히 구 값과 동일했다 — 보장 아님)

## 알려진 스크립트 결함 (고쳐 쓸 것)

1. **`phase5b.sh` 헬스체크** — `curl --retry` 는 connection-refused 를 재시도하지 않는다.
   `--retry-connrefused` 를 넣어야 Spring 부팅(16.5초)을 기다린다. 이번에 오탐 1건 발생.
2. **`phase5b.sh` smoke·게이트 판정** — `[ -n "$SMOKE" ]` / `!= "404"` 라 curl 에러 텍스트·`000` 에도
   통과하는 false pass 가 있었다. 응답 내용을 실제로 검사할 것.
3. **`gate2a.sh` 정렬 비교** — 로케일에 따라 `sort` 순서가 달라 오탐이 났다(수정됨: 양쪽 `LC_ALL=C sort`).

## 시크릿 취급

리터럴 시크릿 0. 값은 런타임에 `~/mmt-backend.env`·`terraform.tfvars` 에서 읽고,
화면에는 **sha256 앞 8~12자와 길이만** 출력한다. 산출물(plan·env·백업)은 리포 밖
`~/mmt-relaunch/`(chmod 700)에 둔다 — `.gitignore` 에 `*.tfplan*`·`*.tfvars*` 를 추가한 이유.
