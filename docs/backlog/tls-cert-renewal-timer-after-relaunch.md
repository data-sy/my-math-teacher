# [Infra] 재런치된 호스트에 certbot 자동갱신 타이머가 살아 있는지 미확인 — TLS 만료 2026-11-03

- **상태:** 🔵 착수 가능 · **기한 있음(2026-11-03)** · 비차단이나 방치하면 사이트 전체가 죽는다
- **등록:** 2026-08-15 (`/refresh-ops-docs` — 소비 문서 정리 중 아카이브에만 묻혀 있던 만료일을 발견)

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
즉 지금 호스트에 타이머가 있는지 **아무도 확인한 적이 없다.**

## 확인 방법 (읽기 전용, 사람 몫 — SSM/SSH)

```bash
systemctl list-timers 'certbot*' --all
sudo certbot certificates                     # 만료일 실측
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot --dry-run
```

## 조치

- 타이머가 **없으면** 재등록(M6 런북 §R5 절차 그대로) + dry-run 실증
- 재런치 런북 §5 에 **"certbot 타이머 재등록"을 명시 단계로 추가** — 재발 방지가 본체다
  (이번에 빠진 이유 = 런북이 "재발급"만 적고 "갱신"을 안 적었다)

## 왜 지금 걸리지 않았나

만료가 2026-11-03 이라 **아직 유효**하다. 그래서 라이브 헬스체크·스모크가 전부 200 이고
증상이 0이다 — 조용히 시한폭탄으로 있다가 만료 당일 사이트 전체가 브라우저 경고로 막힌다.
