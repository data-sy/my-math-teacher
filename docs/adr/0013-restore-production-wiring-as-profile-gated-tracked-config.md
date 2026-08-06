# ADR 0013: 프로덕션 배선을 profile-gated tracked 설정으로 복구 (secure.yml 매핑 소실 대응)

## Status
Accepted (2026-07-27 — 사용자 사전 승인: 옵션 1 "application.yml profile-gated" 선택)

## Context

- 2026-07 시크릿 유출 대응 커밋 `c7f608d`("fix(security): 프로덕션 시크릿 application-secure.yml 추적 해제")가 `application-secure.yml` 을 `git rm --cached` + `.gitignore`(`application-secure*.yml`) 처리했다. 이 파일은 datasource/redis/neo4j/oauth/jwt/cors 를 `${RDS_HOST}`·`${GOOGLE_CLIENT_ID}`·`${JWT_SECRET}` 등 **env 변수를 spring 프로퍼티로 조립하는 배선**을 담고 있었고, 원래 **추적되던 파일**이라 CI 가 체크아웃할 때 딸려와 이미지에 구워졌다 → 프로덕션 부팅 OK.
- 유출 대응이 시크릿뿐 아니라 그 **비밀 아닌 배선 구조까지** 함께 제거했다. 그 이후 CI(`api-ci-cd-with-ec2.yml`)가 빌드한 백엔드 이미지는 이 배선이 없어 프로덕션에서 부팅 실패한다:
  ```
  Failed to configure a DataSource: 'url' attribute is not specified
  ```
  M7 이 유출 대응 이후 첫 CI 백엔드 배포라 이때 처음 표면화(2026-07-27, blue-green green 슬롯 기동 시). blue-green 헬스 게이트가 컷오버 전 abort → 프로덕션 영향 0(구 blue 서빙 유지).
- 호스트 env-file(`~/mmt-backend.env`)은 `RDS_*`·`REDIS_*`·`GOOGLE_*`·`JWT_SECRET`·`EC2_DOMAIN_*` 등 **값만** 제공하고 `SPRING_DATASOURCE_URL` 같은 완성 프로퍼티 키는 없다 → 배선 파일이 반드시 필요.
- 추적됐던 `application-secure.yml`(blob `10ecb485`, `c7f608d^`)은 **전부 `${ENV}` placeholder** 였음을 git 히스토리로 확인(리터럴 시크릿 0). 3중 검증(placeholder 판별·해시 대조·pickaxe)으로 공개 히스토리에 라이브 시크릿 부재도 재확인(`production-deploy-live-resume-link.md` §9). → 배선(값 아님) 복구는 안전.
- 루트/`api` CLAUDE.md 는 datasource·시크릿 해석 구조 변경에 Analyze-Before-Change + ADR 를 요구한다.

## Decision

옛 `application-secure.yml` 의 **placeholder-only 배선을 tracked `application.yml` 안의 profile-gated 멀티도큐먼트로 복구**한다.

1. `application.yml`(이미 tracked) 말미에 `---` 문서 구분자 + `spring.config.activate.on-profile: secure` 문서를 추가하고, 여기에 datasource/redis/neo4j/oauth2/jwt/allowed/logging 배선을 넣는다. **값은 전부 `${ENV}` placeholder — 리터럴 시크릿 0.** 실제 값은 계속 호스트 env-file 에서만 주입.
2. **profile-gated** 이므로 `secure` 프로파일이 활성인 **프로덕션에서만** 로드된다. 로컬 dev(`securelocal`)·테스트(`test`)는 `secure` 미활성이라 이 문서를 읽지 않고, 각각 gitignored `application-securelocal.yml` / `TestcontainersConfig` 로 배선한다 → base/로컬/테스트 무영향.
3. 시크릿-보유 파일명(`application-secure*.yml`, gitignore 대상)은 **재도입하지 않는다** — 그 이름을 다시 tracked 로 만들면 향후 실수로 리터럴이 커밋될 때 gitignore 가 못 잡는다(유출 재발 경로). 배선을 tracked `application.yml` 로 옮기면 이름 충돌·gitignore 예외 규칙이 불필요.

호스트 활성 프로파일은 `secure` 그대로 유지 — **라이브 호스트 env 변경 0**.

## Consequences

### Positive
- CI 가 빌드한 이미지가 다시 프로덕션에서 부팅 가능(datasource/oauth/jwt 배선이 이미지에 구워짐). 비재현 로컬 빌드 패턴(이 결함의 원인) 재도입 불요.
- 리터럴 시크릿 0 — 유출 대응의 보안 태세 유지. 배선만 tracked, 값은 env-file.
- 호스트 무변경으로 복구 — blue-green 다크 배포에 결함 수리를 끼워넣을 때 라이브 호스트 편집(누락 시 동일 부팅 실패 재현)이 없다.
- 로컬/테스트 무영향 검증됨: `securelocal` 부재(CI 조건) + `secure` 프로파일 로컬 부팅 시 옛 DataSource 결함 사라지고 전 배선 해석 확인(2026-07-27, Hikari 가 DB 도달 → 의도적 오패스워드 Access denied 까지 진행).

### Negative
- 프로덕션 배선이 base 와 같은 `application.yml` 파일에 공존(단, profile-gated 라 base 프로퍼티 오염은 없음). 파일이 길어짐.
- `secure` 프로파일 배선이 tracked 라 리뷰 시 리터럴 시크릿 유입을 계속 감시해야 함(placeholder-only 규율) — PR 리뷰 literal 스캔으로 방어.

### Neutral
- 폐기된 `application-secure.yml`(gitignored)은 로컬/호스트 디스크에 남아도 무해(로드 안 됨). 시크릿의 정본은 여전히 env-file/compose.
- 향후 시크릿-보유 파일이 다시 필요하면 `application-secure*.yml` 이름은 gitignore 대상으로 예약된 채 유지.

## Alternatives Considered

1. **신규 tracked `application-prod.yml` + 활성 프로파일 `secure`→`prod` 전환** — 기각. base 를 최소로 유지하는 이점은 있으나, 라이브 호스트 env-file 의 `SPRING_PROFILES_ACTIVE` 를 배포와 lockstep 으로 수정해야 하고, 누락 시 새 이미지가 `secure` 로 부팅→배선 파일 없음→동일 부팅 실패를 재현한다. 결함의 성격(이미지에 배선 부재)상 호스트 편집 의존을 늘리는 건 위험.
2. **CI 빌드 시 GitHub Secret 으로 secure.yml 주입** — 기각. 현 구조 유지되나 secret 관리 오버헤드 + 배선(비밀 아님)을 secret 으로 다루는 비직관. 배선은 tracked 가 정석.
3. **로컬 빌드로 우회** — 기각. 비재현 로컬 빌드 = 이 결함의 근본 원인 재도입. 현 dev 머신엔 secure.yml 부재라 즉시 불가하기도 함.
4. **시크릿 파일명 재추적(`!application-secure.yml` gitignore 예외)** — 기각. 유출 재발 경로(리터럴 실수 커밋을 gitignore 가 못 잡음). c7f608d 가 그 이름을 ignore 한 취지에 반함.

## References
- 관련 ADR: ADR-0007(blue-green 무중단 배포), ADR-0008(SSM 배포 채널), ADR-0011(front 이미지 스왑), ADR-0012(M7 KST 코어)
- 결함 백로그: `docs/backlog/ci-backend-image-missing-secure-yml.md`
- 유출 대응 커밋: `c7f608d` / 런북: `docs/specs/m6/oauth-and-secret-rotation-runbook.md`
- CI 무중단배포 정합: `docs/backlog/production-deploy-live-resume-link.md` §9
