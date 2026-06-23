# ADR 0007: 단일 EC2 무중단 배포를 blue-green + 프론트 nginx 재사용 + 커스텀 헬스로 구축

## Status

Accepted

## Context

M4(배포 무중단화)에서, 단일 EC2 위 Spring Boot 백엔드 재배포 시 발생하던 다운타임(502/요청 유실)을 제거하는 토대를 결정해야 했다. 설계는 `docs/specs/m4/spec-01-zero-downtime-deployment.md` 에서 확정(§7), 구현은 Task A~D 로 완료된 상태에서 그 의사결정을 본 ADR 로 기록한다(spec-01 §7·§8 이 "구현 착수 시 배포 전략 ADR 작성"을 요구).

기존 상태가 무중단을 막던 구조적 원인(spec-01 §1.6):

1. 배포가 기존 컨테이너를 **먼저 제거**(겹침 0) → teardown~JVM 부팅 동안 502.
2. 이미지 태그 `:1.0.0` **고정**(매 빌드 덮어씀) → 신·구 버전 구분·롤백 불가.
3. nginx upstream **하드코딩** + `/oauth2`·`/login/oauth2` 가 다른 타깃으로 갈림 → 런타임 전환 수단 없음.
4. `container_name` **고정** → blue/green 2개 병행 불가.
5. **헬스 신호 부재**(Actuator 미도입) → 전환 게이트로 쓸 신호 없음.

결정에 영향을 준 확정 제약:

- **프리티어 RAM(핵심 제약):** AWS 프리티어 t3.micro = 1 GiB. `2×JVM + MySQL + Neo4j` 동시 구동 불가. blue-green 은 전환 순간 백엔드 2개가 잠깐 공존하므로, 무거운 상태 저장소를 EC2 밖/축소해야만 성립한다.
- **배포 토폴로지:** 프론트(=nginx)와 API 가 동일 호스트(ADR 0006 과 동일 전제). 이 nginx 가 이미 API 리버스프록시를 겸하므로 전환 지점으로 재사용 가능.
- **레지스트리:** 현행 Docker Hub 유지(ECR 전환은 범위 밖).

## Decision

### D1. blue-green (롤링 아님)

신버전을 idle 색(blue↔green) 슬롯에 올리고 → 헬스 통과 확인 → nginx upstream 을 신버전으로 graceful reload → 구버전 드레인. 어느 시점에도 트래픽 받는 정상 컨테이너가 최소 1개 → 다운타임 0. 단일 EC2 + 긴 JVM 부팅 + 즉시 롤백 요구에 가장 단순하고 표준적. 유일한 비용은 전환 구간 백엔드 2개 공존 메모리.

> **RAM 전제(D1 성립 조건, spec-01 §9.3):** MySQL=RDS 분리, Neo4j=미구동(`mmt.migration.use-mysql-cte-for-graph=true`), Redis=EC2 로컬 유지, 2GB 스왑 + 백엔드 `mem_limit 350m`/`-XX:MaxRAMPercentage=70`. 이 전제가 깨지면(예: Neo4j 를 EC2 에 상주) blue-green 대신 롤링/유지보수창 재검토.

### D2. 헬스 신호 = 커스텀 `/api/v1/health` (Actuator 아님)

컨텍스트 기동 후 `200 OK` 만 반환(의존성 검사 없음), Security `permitAll`. 전환 게이트 + 부하 테스트 타깃. 레포가 의도적으로 Actuator 를 배제한 맥락 유지, "프로브 철학"은 Out of scope. 풍부한 헬스가 필요해지면 Actuator 로 승격.

### D3. 전환 지점 = 프론트 nginx 재사용 (전용 프록시 신설 안 함)

`web/nginx.conf` 의 `upstream mmt_backend { include /etc/nginx/active-backend.conf; }` + 세 location(`/api/v1/`·`/oauth2/`·`/login/oauth2`) 타깃 통일. 교체 대상 fragment(`deploy/active-backend.conf`)를 conf.d **밖**(`/etc/nginx/active-backend.conf`)에 볼륨 마운트 → 배포 스크립트가 이 한 줄만 재작성 + `nginx -s reload`. 새 컨테이너 0.

> ⚠️ fragment 를 conf.d 안에 두면 스톡 nginx 의 `include conf.d/*.conf` 가 http 컨텍스트로 이중 로드해 `nginx -t` 가 깨진다 → 반드시 conf.d 밖(spec-01 §3.2, `nginx:1.21.4-alpine` 으로 실측 확인).

### D4. 백엔드 2슬롯 = 스크립트 `docker run` (compose 2서비스 아님)

인프라/프론트/redis 는 compose 에 두고, 백엔드 blue/green 만 `deploy/switch-backend.sh` 의 `docker run` 으로 운용. 동적 sha 태그 주입과 색 전환 제어가 단순. 백엔드는 compose 에서 제거하고 `container_name` 고정도 제거.

- 백엔드 시크릿 env 는 비커밋 `--env-file`(`BACKEND_ENV_FILE`)로만 주입. 더미 `GDB_*`·CTE 플래그·프로파일·JVM 은 스크립트가 inline `-e` 로 박는다(시크릿 아님).
- nginx 가 blue/green 을 이름 해석하려면 백엔드가 front 와 **같은 user-defined 네트워크**여야 함 → `docker run --network <compose_default_net>`(R3).
- 구버전 정리는 `docker stop -t <N ≥ 30s>` 로 graceful 드레인 후 rm. `server.shutdown=graceful` + `timeout-per-shutdown-phase: 30s` 와 짝(N 이 phase 타임아웃보다 짧으면 SIGKILL 로 graceful 이 잘림).

### 배포 파이프라인

`build-and-push` 태그를 `:1.0.0` → `${{ github.sha }}`(immutable), `deploy` job 의 `rm -f→rmi→compose up→prune` 제거 후 SSH 로 `switch-backend.sh <sha>` 호출. 롤백 = 이전 sha 재배포 또는 fragment flip-back + reload.

## Consequences

### Positive
- 배포 중 트래픽 받는 컨테이너가 항상 ≥1 → 구조적 다운타임 0. JVM 부팅 지연이 사용자에게서 격리.
- immutable sha 태그로 신·구 구분·sha 단위 롤백 성립. fragment flip-back 으로 전환 후 즉시 롤백.
- 새 인프라(K8s/ALB/ECS) 도입 0 — 기존 nginx 재사용("과설계 금지").
- "구버전·신버전 병행 + 즉시 롤백" 피처플래그 정책을 구조 자체로 충족(앱 플래그 불요).

### Negative
- 전환 구간 백엔드 2개 공존(≈2×JVM ~700MB) → 1 GiB 빠듯. 스왑 의존이 헬스 폴 false negative/OOM 위험을 부른다(R6) → 헬스 폴 타임아웃·재시도 여유 + 보수적 mem_limit + 전환 후 즉시 드레인으로 완화. 그래도 불안정하면 D1 전제가 깨진 것 → 인스턴스 상향.
- 백엔드가 compose 밖으로 나가 상태가 스크립트에 분산(선언성↓). compose 와 스크립트 두 곳을 봐야 전체 토폴로지 파악.
- "유실률 0%" 클레임은 graceful drain + 대표 엔드포인트 측정에서만 의미. sub-ms `/health` 단독 측정은 drain 경계 502 를 못 드러냄(R5) → 검증은 spec-01 §4 설계 준수 필요.

### Neutral
- 헬스가 DB/Redis 를 검사하지 않음(준비됨만 표현). 의존성 게이팅은 의도적 Out of scope.
- HTTPS·OAuth 로그인은 후속(R2, 2026-06-15 후속 유지 확정). 무중단 메커니즘은 HTTP 80 으로 증명.
- Neo4j 미구동은 M3(Neo4j 폐기) 방향과 정합 — M4 bring-up 이 M3 go/no-go 입력을 준다(M4→M3 역방향·비차단).

## Alternatives Considered

1. **롤링(축차 교체)** — 기각. 단일 EC2(인스턴스 1~2개)라 이점 미미하고 부분 상태로 롤백이 더 복잡. blue-green 의 원자적 전환·flip-back 이 단순.
2. **Spring Boot Actuator `/actuator/health`** — 기각. 레포가 의도적으로 Actuator 미도입(MeterRegistry 수동 등록). 200 ping 수준 게이트엔 컨트롤러 1개로 충분. 필요 시 후속 승격.
3. **전용 리버스 프록시 컨테이너 신설** — 기각. 관심사 분리는 깔끔하나 이번 범위엔 과함. 이미 있는 프론트 nginx 로 충분.
4. **compose 서비스 2개(blue/green)** — 기각. 동적 sha 태그 주입이 compose 변수로 번거로움. 스크립트 `docker run` 이 색 전환 제어에 유연.
5. **`docker stop` 기본 grace(10초) 사용** — 기각. `timeout-per-shutdown-phase`(30s)보다 짧아 graceful 이 SIGKILL 로 잘림 → 컷오버 구간 502. `-t ≥30` 명시.

## References

- 적용 spec: `docs/specs/m4/spec-01-zero-downtime-deployment.md`(§7 결정 D1~D4·§9 프로비저닝·§10 리스크 R1~R8), `spec-02`(하네스 게이트), `spec-04`(사람 프로비저닝 핸드오프)
- 구현 커밋(브랜치 `feat/m4-spec-01-zero-downtime-deployment`): A 헬스 `025daf1` / A.5 graceful `5da8d9b` / R1 기동증명 `1cc6efd` / B nginx 전환구조 `3491a55` / **C `switch-backend.sh` `ce3ccc7`** / **D 워크플로 immutable 태그 `abd08af`**
- 핵심 파일: `deploy/switch-backend.sh`, `deploy/active-backend.conf`, `web/nginx.conf`, `api/.../controller/HealthController.java`, `api/src/main/resources/application.yml`(graceful), `.github/workflows/api-ci-cd-with-ec2.yml`
- 관련 ADR: ADR 0006(동일 호스트 nginx same-origin 토폴로지 전제 공유)
- 관련 규칙: 루트 `CLAUDE.md` 피처플래그·마이그레이션·ADR 규칙
