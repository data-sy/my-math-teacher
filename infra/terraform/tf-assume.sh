# infra/terraform/tf-assume.sh
#
# Phase B(real AWS) 자격 헬퍼. mmt-admin 프로필에 정의된 IAM role 을 MFA 로 assume 해
# 임시자격을 *현재 셸* 에 AWS_* 환경변수로 export 한다. 이후 terraform plan/apply 가
# 이 임시자격을 사용한다.
#
# 왜 필요한가: Terraform 의 AWS provider 는 shared-config 프로필의 MFA assume-role 를
# 대화형으로 처리하지 못한다("AssumeRoleTokenProvider session option not set").
# AWS CLI 는 MFA 를 프롬프트하므로, CLI 로 assume 한 임시자격을 env 로 넘겨준다.
# 보안 모델은 그대로: IAM user(mmt-base)의 정적 키는 sts:AssumeRole 전용, admin 권한은
# MFA 로 assume 한 임시자격으로만. 장기 admin 키 없음.
#
# 사용법(반드시 source — 실행이 아니라 현재 셸에 export 해야 함):
#   source infra/terraform/tf-assume.sh
#   terraform plan          # 만료 1h → 재-source
#
# 프로필 이름은 아래 두 변수로 조정(기본 mmt-base / mmt-admin).

_p_base="${TF_ASSUME_BASE_PROFILE:-mmt-base}"
_p_admin="${TF_ASSUME_ADMIN_PROFILE:-mmt-admin}"

_role_arn="$(aws configure get role_arn --profile "$_p_admin" 2>/dev/null)"
_mfa_serial="$(aws configure get mfa_serial --profile "$_p_admin" 2>/dev/null)"
_duration="$(aws configure get duration_seconds --profile "$_p_admin" 2>/dev/null)"
: "${_duration:=3600}"

if [ -z "$_role_arn" ] || [ -z "$_mfa_serial" ]; then
  echo "tf-assume: '$_p_admin' 프로필에서 role_arn/mfa_serial 을 못 읽음 (~/.aws/config 확인)" >&2
else
  printf "tf-assume: MFA code for %s: " "$_mfa_serial"
  read -r _code
  _creds="$(aws sts assume-role \
    --profile "$_p_base" \
    --role-arn "$_role_arn" \
    --serial-number "$_mfa_serial" \
    --token-code "$_code" \
    --role-session-name terraform \
    --duration-seconds "$_duration" \
    --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \
    --output text 2>&1)"
  if [ $? -ne 0 ] || [ -z "$_creds" ]; then
    echo "tf-assume: assume-role 실패 → $_creds" >&2
  else
    export AWS_ACCESS_KEY_ID="$(printf '%s' "$_creds" | cut -f1)"
    export AWS_SECRET_ACCESS_KEY="$(printf '%s' "$_creds" | cut -f2)"
    export AWS_SESSION_TOKEN="$(printf '%s' "$_creds" | cut -f3)"
    echo "tf-assume: 임시자격 export 완료 (${_duration}s). 이제 'terraform plan' 실행 가능."
  fi
fi

unset _p_base _p_admin _role_arn _mfa_serial _duration _code _creds
