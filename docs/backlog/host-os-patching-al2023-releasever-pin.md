# [Infra] 호스트가 OS 보안 패치를 받지 않는다 — AL2023 releasever 가 AMI 스냅샷에 고정

**등록:** 2026-08-31 (AMI 필터 지뢰 제거 중 파생 발견) · **상태:** ✅ **해결 (2026-08-31)** — 러닝 호스트 적용 완료 + 다음 런치 배선 ·
**분류:** 운영 위생 / 보안 · **관련:** [AMI 필터 지뢰](ami-filter-picks-minimal-no-ssm-agent.md)

> **한 줄:** `dnf check-update` 가 **0건**이라 안전해 보이지만, AL2023 은 releasever 를
> **AMI 빌드 스냅샷에 고정**한다. `--releasever=latest` 로 물으면 **11건**이 대기 중이고
> 그중에 `openssh-server`·`kernel6.18`·`docker` 가 있다. 즉 **거짓 안심**이다.

## 실측 (2026-08-31, `i-098e63bf15a150633`)

```
releasever(실제 해석값) = 2023.12.20260803     ← AMI 빌드 스냅샷에 고정
최신 스냅샷              = 2023.12.20260817     ← 2주 뒤처짐

dnf -q check-update                     → exit 0,   0건   ← 거짓 0
dnf -q --releasever=latest check-update → exit 100, 11건  ← 진실
```

대기 중인 11건 중 눈에 띄는 것:

| 패키지 | → 버전 | 왜 중요한가 |
|---|---|---|
| `openssh` / `-server` / `-clients` | `9.9p1-10` | sshd = 인바운드 22 노출(내 IP 한정이지만) |
| `kernel6.18` | `6.18.39` → `6.18.41-94.142` | **재부팅 필요** |
| `docker` / `containerd` | `25.0.16` / `2.2.5` | **데몬 재시작 = 백엔드 컨테이너 다운** |
| `python3-idna` · `perl-Net-SSLeay` · `system-release` 외 | | |

## 왜 이렇게 되나 (AL2023 의 의도된 설계)

AL2023 은 "deterministic upgrades" 를 표방해 `/etc/dnf/vars/releasever` 로 리포지토리를
특정 스냅샷에 묶는다. 같은 AMI 에서 뜬 인스턴스는 몇 달 뒤에 떠도 **같은 패키지 집합**을 받는다 —
재현성에는 좋지만, **가만두면 영원히 패치되지 않는다**는 뜻이기도 하다.
`dnf update` 는 "핀 안에서" 최신이라 정직하게 0건을 보고한다. 함정은 dnf 가 아니라 핀이다.

## 이력 — 이 호스트는 한 번도 패치된 적이 없다

`dnf history` 에 트랜잭션이 **3건뿐이고 전부 install** 이다:

```
3 | install -y mariadb105     | 2026-08-15   (운영 중 수동)
2 | install -y amazon-ssm-agent | 2026-08-06 (ssm-recover.sh)
1 | install -y docker         | 2026-08-05   (user_data 부트스트랩)
```

`update` 가 0건이다. 인스턴스는 2026-08-05 기동이고 AMI 는 2026-08-03 빌드 —
**26일째 무패치**다. `dnf-automatic` 은 설치돼 있지 않고 타이머도 없다.

> ⚠️ **`dnf-automatic` 만 깔아도 해결되지 않는다.** 그것도 핀을 존중하므로 0건을 보고하고
> 아무것도 하지 않는다. 핀을 어떻게 다룰지가 본질이다.

## 노출면 (위험 보정)

```
80/443 → 0.0.0.0/0    인터넷 직접 = nginx
22     → my_ip/32     IP 고정
8080   → 룰 없음       앱 포트 외부 비공개
```

원격 선제 공격 표면은 사실상 nginx 하나다. OS 패키지 CVE 다수는 로컬 권한상승 계열이라
즉시 치명적이지는 않다. **다만 이 항목의 요점은 "지금 뚫린다"가 아니라
"방치하면 무기한 누적되고, 계기판이 0건이라 알아챌 수 없다"** 는 것이다.

## 결정거리

- **P1 — 핀을 어떻게 다룰까**
  - (a) `/etc/dnf/vars/releasever` 를 `latest` 로 → 상시 최신 추종. 재현성을 잃는다.
  - (b) 핀은 두고 **주기적으로 `dnf update --releasever=latest`** (이러면 `system-release` 가
    갱신되며 핀이 다음 스냅샷으로 전진한다) → 결정론을 유지하되 사람이/타이머가 민다.
- **P2 — 자동 적용 범위**: 보안만(`--security`) vs 전체. 전체는 docker/containerd 를 물어
  **컨테이너 런타임 재시작 = 서비스 중단**을 부른다.
- **P3 — 재부팅 정책**: 커널 업데이트는 재부팅이 필요하다. 단일 EC2 상시 서비스라
  **자동 재부팅은 위험**하다. 권장 = 자동 적용 O / 자동 재부팅 X, 재부팅은 사람이 창을 잡는다.
- **P4 — user_data 에도 넣을까**: 다음 런치부터 기본 탑재. AMI 필터 작업의 `ignore_changes`
  덕분에 **러닝 호스트 무접촉으로 config 만 갱신**된다(plan 0 change).

## 착수 시 주의

- **최초 적용은 다운타임을 동반한다.** docker/containerd 갱신은 데몬 재시작이고,
  커널은 재부팅이다. blue-green 은 **같은 호스트 안**이라 이 경우 보호막이 되지 않는다.
- 되돌리기 어렵다 — `dnf history undo` 가 있으나 커널·런타임 계열은 실전 롤백이 지저분하다.
  **적용 전 스냅샷/AMI 를 떠 두는 편이 안전**하다.
- 순서 권장: 보안만 우선 적용 → 서비스 확인 → 커널/런타임은 창을 잡아 별도로.

## 해결 (2026-08-31)

결정: **P1 = (a) 핀 해제** · **P2/P3 = 전체 적용 + 재부팅을 창 한 번에** ·
**자동화 = 감지만 자동, 적용은 사람** · **P4 = user_data 반영**.

### 적용한 것

| | 무엇 | 어디 |
|---|---|---|
| 핀 해제 | `/etc/dnf/vars/releasever` = `latest` | 러닝 호스트 (SSM) + `user_data` |
| 전체 업데이트 | 11건 적용 (Upgraded 10 · Installed 1) | 러닝 호스트 |
| 감지 자동화 | `dnf-automatic` — `download_updates=yes` / `apply_updates=no` / `emit_via=motd,stdio`, 타이머 활성 | 러닝 호스트 + `user_data` |
| 다음 런치 배선 | 위 3개를 부트스트랩에 | `compute.tf` user_data (커밋 `4004886`) |

`ignore_changes = [ami, user_data]` 덕분에 user_data 변경은 러닝 인스턴스를 건드리지 않았다
(plan `0 to destroy`). 러닝 호스트에는 같은 내용을 SSM 으로 별도 적용했다.

### 검증

```
실행 커널  6.18.39-79.141  →  6.18.41-94.142   (재부팅 반영)
docker     25.0.16  ·  containerd  2.2.5        (Important 권고 2건 해소)
openssh    9.9p1-10  ·  system-release 20260817
releasever = latest  (system-release 업그레이드를 넘어 잔존)

계기판이 진실을 말하는가
  dnf check-update                    → 0건
  dnf --releasever=latest check-update → 0건   ← 두 값이 일치 = 거짓 0 해소
  보안 권고 잔여                        → 0건

서비스
  https://www.my-math-teacher.com/                   → 200
  https://www.my-math-teacher.com/api/v1/concepts/5814 → 200 (정상 JSON)
  컨테이너 4개 전부 복귀 · redis 해석 정상 · HikariPool Start completed
  재부팅 이후 ERROR 1건 = 아래 기존 버그를 직접 찔러 만든 것
```

롤백 보험으로 착수 전 루트 EBS 스냅샷 `snap-048134aa5a4e4db7e` 를 떴다(completed).
문제가 없었으므로 **불필요해지면 지우면 된다** — 남겨두면 스토리지 요금이 계속 붙는다.

### 다운타임 실측

두 번 났고 둘 다 자동 복귀했다. 컨테이너 `restart: unless-stopped` + `docker.service` 부팅
활성이 이걸 받아냈다 — **프리플라이트에서 이걸 먼저 확인한 게 결정적이었다.** 이게 아니었으면
재부팅이 무기한 중단이 됐다.

1. **docker/containerd 업그레이드 (02:53)** — 데몬 재시작으로 컨테이너 4개가 함께 재기동.
   백엔드 로그에 `mmt-redis: Name does not resolve` 가 몇 건 찍혔으나 재기동 완료 후 소멸(현재 0건).
2. **재부팅 (02:56)** — 약 1분. 복귀 후 전 항목 정상.

### 확인된 것 — 이건 회귀가 아니다

`/api/v1/concepts/{없는 id}` → **500**(`EmptyResultDataAccessException: expected 1, actual 0`).
404 미처리라는 **기존 버그**이며, 같은 예외가 패치 착수(02:45) 전인 **02:11 에도** 찍혀 있다.
실존 id(5814·5485)로는 200 이 나온다. 별도 항목으로 남는다.

### 남은 것

- **알림이 호스트 안에만 머문다.** `emit_via = motd,stdio` 라 대기 업데이트를 보려면
  호스트에 들어가야 한다. 사용자에게 닿는 경로(메일·CloudWatch 등)는 배선하지 않았다 —
  **"감지 자동"은 현재 "호스트가 알고 있다"까지고, "내가 안다"까지는 아니다.**
- **적용 창은 여전히 사람이 잡는다**(설계상 의도). docker/containerd 권고가 또 뜨면
  같은 다운타임을 다시 치러야 한다.
- 스냅샷 `snap-048134aa5a4e4db7e` 정리 여부.
