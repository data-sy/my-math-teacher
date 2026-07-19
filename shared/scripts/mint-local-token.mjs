// 로컬 검증용 액세스 토큰 주조 — securelocal jwt.secret(HS256, base64) 사용
import { readFileSync } from 'node:fs'
import { createHmac } from 'node:crypto'

const yml = readFileSync('/Users/owner/my-math-teacher/api/src/main/resources/application-securelocal.yml', 'utf8')
const m = yml.match(/jwt:\s*\n(?:.*\n)*?\s*secret:\s*(\S+)/)
if (!m) { console.error('jwt.secret not found'); process.exit(1) }
const key = Buffer.from(m[1], 'base64')

const b64url = (buf) => Buffer.from(buf).toString('base64url')
const header = b64url(JSON.stringify({ alg: 'HS256' }))
const sub = process.argv[2] ?? 'm7smoke01'
const exp = Math.floor(Date.now() / 1000) + 3600 * 6
const payload = b64url(JSON.stringify({ sub, auth: 'ROLE_USER', exp }))
const sig = createHmac('sha256', key).update(`${header}.${payload}`).digest('base64url')
console.log(`${header}.${payload}.${sig}`)
