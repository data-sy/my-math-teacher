# Backlog: Grafana + Prometheus 로 무중단 배포 재계측 (관측성 학습 겸용)

**분류:** [Observability] / 학습(chosen) — 백로그
**관련:** M4 spec-01 §4(유실률 측정), `docs/benchmark/milestone-4-run-report.md`(로그 기반 3단 결론), `milestone-4-zero-downtime-report-{eng,ko}.html`(시각 리포트)
**상태:** 미착수. M4 무중단 배포는 **로그 기반(k6 요약 + `run-log.sh` docker stats 점 스냅샷)으로 이미 정량 증명 완료.** 이 백로그는 correctness 가 아니라 **관측성·자기문서화·포트폴리오 설득력**을 올린다.

> M4 는 로깅 하네스로 "사후" 증명했다. 이 항목은 같은 무중단 컷오버를 **Prometheus 로 실시간 스크레이프 + Grafana 로 상관 시각화**해, "실시간으로 실측 중"임을 그림으로 보여주는 재실험이다. 결론은 안 바뀐다 — 표현과 관측 인프라가 바뀐다.

---

## 1. 왜 백로그인가 (요약)

현재 증명은 **정량적으로 sound** 하다: k6 의 `status_502`/`transport_err`/latency 가 부하측 진실이고, `run-log.sh ec2 <eip> cutover` 가 컷오버 순간 `docker stats` 를 캡처한다. 부족한 건 정확성이 아니라 **(a) 연속성**(점 스냅샷 → 스크레이프 곡선), **(b) 상관**(부하·nginx·JVM·컨테이너를 한 타임축에), **(c) 공유가능한 그림**(큐레이션 로그보다 대시보드 스크린샷이 설득력 높음).

목적이 **"Grafana/Prometheus 가 뭔지 경험"** 이면 이건 좋은 첫 소재다 — 토이 대시보드가 아니라 *실제로 증명할 게 있는* 이벤트(컷오버 순간)를 계측하므로 동기부여가 되고, exporter → scrape → PromQL → 패널의 코어 파이프라인을 다 만진다.

## 2. 무엇을 계측하나 (컷오버 순간을 한 타임축에)

리포트의 "부팅 JVM 148% 가 서빙 JVM 을 굶긴다" 서사를 **점 3개 → 상관된 실시간 곡선**으로 만든다.

| 신호 | 소스 | exporter / 엔드포인트 | 컷오버 때 보이는 것 |
|---|---|---|---|
| 부하측 에러율·지연·dropped_iter | **k6** | Prometheus remote-write (`-o experimental-prometheus-rw`) 또는 web dashboard | 5xx 라인 바닥(0) 유지, p95 스파이크 |
| upstream 2xx/5xx (blue vs green) | **nginx** | `nginx-prometheus-exporter` (+ stub_status) | blue 드레인 ↔ green 픽업, 502=0 |
| http_server_requests · JVM CPU/mem | **Spring Boot** | `/actuator/prometheus` (아래 dep 추가 필요) | 부팅 JVM CPU 스파이크가 서빙 지연 미는 순간 |
| 컨테이너/호스트 CPU·mem | **host** | `cAdvisor` + `node_exporter` | 148%→55% 곡선(현재는 점 스냅샷) |

**추가로 붙일 만한 것 (측정가능한 것 다 모으기):**
- **복합인덱스 EXPLAIN 스크린샷** — `idx_knowledge_space_composite` "Using index"(커버링) 를 시각 증거로. (M2 CTE 마이그레이션 산출물, 지금은 텍스트로만 있음.)
- MySQL exporter(`mysqld_exporter`) → RDS 커넥션풀·쿼리 지연.
- Redis exporter → 캐시 hit/miss(캐시픽스 크로스인스턴스 효과 시각화).

## 3. 무엇을 추가해야 하나

- **앱 dep(ADR 필요 — 앱 의존성 변경):** `spring-boot-starter-actuator` + `micrometer-registry-prometheus`. 현재 `micrometer-core` 만 있고 **Actuator 미도입**(MeterRegistry 는 `ObservabilityConfig` 수동 등록) → `/actuator/prometheus` 없음. 노출 시 `management.endpoints.web.exposure` + Security permitAll 경계 재검토(G6 성격).
- **측정 EC2 compose 확장:** `prometheus` + `grafana` + `cadvisor` + `node_exporter` (+ nginx/mysql/redis exporter). front+redis 만 있던 M4 compose에 관측 스택 추가.
- **k6:** web dashboard(`K6_WEB_DASHBOARD=true`, env 하나 — 구 백로그 "A")를 이 런에 **접어서 포함**. 별도 A 런 안 함(인프라 사이클 중복 회피).

## 4. 솔직한 경계 (기대치 정렬)

1. **결론은 안 바뀐다.** Grafana 는 k6 가 이미 뽑은 같은 숫자를 그림으로 겹칠 뿐 — 더 정확한 데이터가 아니라 더 legible 한 표현이다. "measurement 를 다시 한다"기보다 "같은 measurement 를 관측 계측하며 다시 돌린다".
2. **또 한 번의 `apply→측정→destroy` 사이클**을 태운다(MFA·크레딧·~30-40분). 무대(EC2 t3.micro 1 vCPU)가 있어야 컷오버 CPU 경합이 재현되므로 로컬로는 못 한다.
3. **관측 스택이 t3.micro 를 더 짓누른다.** Prometheus/Grafana/cAdvisor 가 같은 1 vCPU·1 GiB 를 나눠 쓰면 측정 대상(무중단)에 관측자 효과가 낀다 → 관측 스택을 별도 인스턴스로 빼거나 t3.small 로 올릴지 결정 필요(측정 페어니스).

## 5. 단계 (착수 시 별도 Task 로 분리)

1. `[ADR]` actuator/micrometer-registry-prometheus 도입 + `/actuator/prometheus` 노출 경계(permitAll 표면).
2. `[compose]` 관측 스택 서비스 + scrape config(`prometheus.yml`) + Grafana 프로비저닝(datasource+dashboard as-code).
3. `[dashboard]` 컷오버 상관 패널: k6 5xx · nginx upstream · JVM CPU · 컨테이너 CPU 를 한 타임축. + 복합인덱스 EXPLAIN 스크린샷.
4. `[run]` apply→시드→프로비저닝(+관측)→배포→**컷오버 부하(k6 web dashboard on)**→패널 스크린샷 캡처→destroy.
5. `[docs]` 스크린샷을 `milestone-4-zero-downtime-report-*.html`/run-report 에 편입(로그 서사 → 그림 증거).

## 6. 경계 요약

- **차단 요소 아님:** M4 무중단 배포는 로그 기반으로 이미 증명·PR 대상. 이건 순수 선택적 관측성/학습 항목.
- **비용:** 인프라 사이클 1회 추가(구 백로그 A = k6 web dashboard 는 여기 접힘, 별도 런 없음).
- **산출물:** 컷오버 상관 대시보드 스크린샷 + 복합인덱스 EXPLAIN 스크린샷 → 시각 리포트 강화.
