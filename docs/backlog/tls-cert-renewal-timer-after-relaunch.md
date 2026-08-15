# [Infra] 프로덕션에 TLS 자동갱신 경로가 **없다** (실측 확정) — 만료 2026-11-03

- **상태:** 🔴 **확정 · 기한 있음** — 만료 `2026-11-03 02:48 GMT`(실측), 약 80일 남음
- **등록:** 2026-08-15 (`/refresh-ops-docs` — 소비 문서 정리 중 아카이브에만 묻혀 있던 만료일을 발견)
- **확정:** 2026-08-15 호스트 실측(`host-readiness-check.sh`)

> ## 실측 결과 — 가설이 아니라 사실이다
>
> | 항목 | 실측 |
> |---|---|
> | 인증서 만료 | `Nov 3 02:48:40 2026 GMT` (openssl, 호스트 실파일) |
> | systemd 타이머 | **0 건** |
> | systemd 유닛 | **0 건** |
> | cron 항목 | **0 건** (`crontab -l` + `/etc/crontab` + `/etc/cron.d/*`) |
> | certbot 바이너리 | **없음** |
>
> 즉 **갱신을 돌릴 주체가 호스트에 존재하지 않는다.** 재런치(2026-08-05)가 docker
> `certbot/certbot` 으로 **1회성 발급만** 하고 끝났고, M6 에서 실증했던 갱신 타이머는
> terminate 된 옛 호스트와 함께 소멸했다. 아무도 안 건드리면 만료 당일 사이트 전체가
> 브라우저 경고로 막힌다.

## 무엇이 문제인가

현재 프로덕션 TLS 인증서는 **Let's Encrypt · `CN=www.my-math-teacher.com` · 만료 `2026-11-03`**
(근거: [`../handoff/archive/🤖-M7-재런치-핸드오프.md`](../handoff/archive/🤖-M7-재런치-핸드오프.md) §상태표).
Let's Encrypt 는 90일이라 **갱신이 자동으로 돌지 않으면 반드시 만료**한다.

M6 최초 배포에서는 자동갱신이 **실증까지 끝나 있었다** — systemd `certbot-renew.timer`
(주간 Mon 03:30 + 지연), webroot dry-run "all simulated renewals succeeded"
([`../specs/m6/first-deploy-runbook.md`](../specs/m6/first-deploy-runbook.md) §R5).

**그런데 그 호스트는 2026-07-31 mothball 로 terminate 됐고, 2026-08-05 재런치는 EBS 를 포함해
호스트를 새로 만들었다.** 재런치 런북 §5 는 "certbot 인증서 재발급"만 지시하고
**갱신 타이머 재등록은 어디에도 없다**([`../handoff/🤖-M7-인프라-티어다운-재런치.md`](../handoff/🤖-M7-인프라-티어다운-재런치.md)).
2026-08-15 실측으로 **타이머가 없다는 것이 확정됐다**(위 표).

## 조치 — 갈림길이 하나 있다

호스트에 certbot 바이너리가 없으므로 **갱신 주체를 무엇으로 둘지**부터 정해야 한다.
두 안 모두 M6 이 깔아 둔 webroot 경로(`/var/www/certbot` + front nginx 의
`/.well-known/acme-challenge/` 예외)를 재사용하므로 **무중단 갱신**인 점은 같다.

| 안 | 내용 | 장단 |
|---|---|---|
| **A. docker certbot + systemd 타이머** (권장) | 재런치가 쓴 방식 그대로 주 1회 `docker run --rm certbot/certbot renew --webroot` | 호스트에 패키지를 안 늘린다(minimal 유지). 재런치 때 쓴 명령과 같아 검증된 경로 |
| B. 호스트에 certbot 설치 + 배포판 타이머 | `dnf install certbot` | M6 런북 §R5 와 같은 모습이지만 minimal AMI 에 패키지를 또 얹고, 재런치마다 반복된다 |

어느 쪽이든 **dry-run 실증**까지가 조치의 끝이다:
```bash
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot --dry-run
# → "all simulated renewals succeeded" 여야 한다
```

## 재발 방지가 본체다

재런치 런북 §5 가 **"인증서 재발급"만 적고 "갱신"을 안 적어서** 이번에 빠졌다.
조치와 함께 런북에 갱신 경로 재등록을 명시 단계로 넣지 않으면 **다음 재런치에서 그대로 재발한다.**

## 왜 지금 증상이 0인가

만료가 2026-11-03 이라 **아직 유효**하다. 라이브 헬스체크·스모크·CD 배포까지 전부 200 이고
아무 신호도 없다 — 조용히 있다가 만료 당일 한 번에 막히는 형태의 결함이다.
이번에도 라이브 관측이 아니라 **호스트 실물 조회**로만 드러났다.
