#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

readonly DEPLOY_DIR="/opt/forge-flow"
readonly COMPOSE_FILE="$DEPLOY_DIR/docker-compose.prod.yml"
readonly SECRET_ENV="$DEPLOY_DIR/.env"
readonly ACTIVE_IMAGE_ENV="$DEPLOY_DIR/state/image.env"
readonly CANDIDATE_IMAGE_ENV="$DEPLOY_DIR/state/image.next.env"
readonly IMAGE_REPOSITORY="ghcr.io/quashy/forge-flow-demo"

original_command="${SSH_ORIGINAL_COMMAND:-}"
if [[ ! "$original_command" =~ ^deploy[[:space:]]([0-9a-f]{40})$ ]]; then
    echo "拒绝未授权的部署命令" >&2
    exit 64
fi

revision="${BASH_REMATCH[1]}"
candidate_image="$IMAGE_REPOSITORY:sha-$revision"
previous_image_id="$(sudo docker inspect --format '{{.Image}}' forge-flow-app 2>/dev/null || true)"

cleanup() {
    rm -f "$CANDIDATE_IMAGE_ENV"
    sudo docker logout ghcr.io >/dev/null 2>&1 || true
}
trap cleanup EXIT

sudo docker login ghcr.io --username Quashy --password-stdin >/dev/null
printf 'APP_IMAGE=%s\n' "$candidate_image" > "$CANDIDATE_IMAGE_ENV"

compose_candidate=(
    sudo docker compose
    --project-name forge-flow
    --env-file "$SECRET_ENV"
    --env-file "$CANDIDATE_IMAGE_ENV"
    --file "$COMPOSE_FILE"
)

"${compose_candidate[@]}" pull db app
"${compose_candidate[@]}" up --detach --no-build

healthy=false
for _ in {1..30}; do
    if curl --fail --silent --show-error http://127.0.0.1:8080/api/meta >/dev/null; then
        healthy=true
        break
    fi
    sleep 2
done

if [[ "$healthy" != true ]]; then
    echo "新版本健康检查失败" >&2
    if [[ -n "$previous_image_id" ]]; then
        rollback_image="forge-flow-demo:rollback"
        sudo docker tag "$previous_image_id" "$rollback_image"
        printf 'APP_IMAGE=%s\n' "$rollback_image" > "$CANDIDATE_IMAGE_ENV"
        "${compose_candidate[@]}" up --detach --no-deps --no-build app
        echo "已恢复上一版本容器" >&2
    fi
    exit 1
fi

mv -f "$CANDIDATE_IMAGE_ENV" "$ACTIVE_IMAGE_ENV"
echo "部署完成：$candidate_image"
