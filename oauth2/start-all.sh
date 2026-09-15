#!/usr/bin/env bash
#
# start-all.sh — Relance complète de la démo OAuth2/OIDC.
#
# Étapes :
#   1. Démarre Keycloak depuis Docker ou une installation locale,
#      avec réimportation du realm `keycloak/realm-demo.json`.
#   2. Attend que le realm `demo` réponde.
#   3. Build complet des 3 modules (sans tests).
#   4. Lance les 3 services Spring Boot en arrière-plan, logs dans /tmp/*.log.
#
# Usage :
#   ./start-all.sh --docker         # tout (re)lancer avec Keycloak Docker
#   ./start-all.sh --local          # tout (re)lancer avec Keycloak local
#   ./start-all.sh stop --docker    # tout arrêter dans le mode choisi
#
# Prérequis : mvn, curl, jq (optionnel), et Docker ou Keycloak local.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

KEYCLOAK_URL="http://localhost:8080/realms/demo"
LOG_DIR="/tmp"
LOCAL_KEYCLOAK_HOME="${KEYCLOAK_HOME:-/home/fabien/dev/tools/keycloak-26.7.3}"
LOCAL_KEYCLOAK_BIN="$LOCAL_KEYCLOAK_HOME/bin/kc.sh"
LOCAL_KEYCLOAK_PID_FILE="$LOG_DIR/prez-oauth2-keycloak.pid"
LOCAL_KEYCLOAK_LOG_FILE="$LOG_DIR/keycloak.log"
REALM_FILE="$SCRIPT_DIR/keycloak/realm-demo.json"

usage() {
    echo "Usage: $0 --docker|--local"
    echo "       $0 stop --docker|--local"
    echo ""
    echo "Options :"
    echo "  -d, --docker    Utiliser Keycloak dans Docker"
    echo "  -l, --local     Utiliser Keycloak depuis $LOCAL_KEYCLOAK_HOME"
    echo "  -h, --help      Afficher cette aide"
    exit "${1:-1}"
}

stop_local_keycloak() {
    if [[ ! -f "$LOCAL_KEYCLOAK_PID_FILE" ]]; then
        echo "ℹ️  Aucun Keycloak local démarré par ce script."
        return
    fi

    local keycloak_pid
    read -r keycloak_pid <"$LOCAL_KEYCLOAK_PID_FILE"
    if [[ "$keycloak_pid" =~ ^[0-9]+$ ]] && kill -0 "$keycloak_pid" 2>/dev/null; then
        echo "🛑 Arrêt de Keycloak local…"
        kill -- "-$keycloak_pid" 2>/dev/null || kill "$keycloak_pid"
        wait "$keycloak_pid" 2>/dev/null || true
    fi
    rm -f "$LOCAL_KEYCLOAK_PID_FILE"
}

stop_all() {
    echo "🛑 Arrêt des services Spring Boot…"
    pkill -f spring-boot:run 2>/dev/null || true
    if [[ "$KEYCLOAK_MODE" == "docker" ]]; then
        echo "🛑 Arrêt de Keycloak Docker (avec suppression du volume)…"
        docker compose down -v
    else
        stop_local_keycloak
    fi
    echo "✅ Tout est arrêté."
}

KEYCLOAK_MODE=""
KEYCLOAK_MODE_SET=false
ACTION="start"

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--docker)
            if [[ "$KEYCLOAK_MODE_SET" == true && "$KEYCLOAK_MODE" != "docker" ]]; then
                echo "❌ Les options --docker et --local sont exclusives." >&2
                usage
            fi
            KEYCLOAK_MODE="docker"
            KEYCLOAK_MODE_SET=true
            ;;
        -l|--local)
            if [[ "$KEYCLOAK_MODE_SET" == true && "$KEYCLOAK_MODE" != "local" ]]; then
                echo "❌ Les options --docker et --local sont exclusives." >&2
                usage
            fi
            KEYCLOAK_MODE="local"
            KEYCLOAK_MODE_SET=true
            ;;
        stop)
            if [[ "$ACTION" == "stop" ]]; then
                usage
            fi
            ACTION="stop"
            ;;
        -h|--help)
            usage 0
            ;;
        *)
            echo "Option inconnue : $1" >&2
            usage
            ;;
    esac
    shift
done

if [[ "$KEYCLOAK_MODE_SET" == false ]]; then
    echo "❌ Une des options --docker ou --local est obligatoire." >&2
    usage
fi

if [[ "$ACTION" == "stop" ]]; then
    stop_all
    exit 0
fi

if [[ "$KEYCLOAK_MODE" == "docker" ]]; then
    echo "🧹 [1/4] Réinitialisation de Keycloak Docker (down -v pour réimporter le realm)…"
    docker compose down -v >/dev/null 2>&1 || true
    docker compose up -d
    KEYCLOAK_LOG_HINT="docker logs keycloak"
else
    if [[ ! -x "$LOCAL_KEYCLOAK_BIN" ]]; then
        echo "❌ Keycloak local introuvable : $LOCAL_KEYCLOAK_BIN" >&2
        exit 1
    fi
    if [[ ! -f "$REALM_FILE" ]]; then
        echo "❌ Fichier de realm introuvable : $REALM_FILE" >&2
        exit 1
    fi

    stop_local_keycloak
    echo "🧹 [1/4] Réimportation du realm dans Keycloak local…"
    "$LOCAL_KEYCLOAK_BIN" import --file "$REALM_FILE" --override true
    echo "🚀 Démarrage de Keycloak local…"
    KC_BOOTSTRAP_ADMIN_USERNAME=admin KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
        nohup setsid "$LOCAL_KEYCLOAK_BIN" start-dev >"$LOCAL_KEYCLOAK_LOG_FILE" 2>&1 &
    printf '%s\n' "$!" >"$LOCAL_KEYCLOAK_PID_FILE"
    KEYCLOAK_LOG_HINT="$LOCAL_KEYCLOAK_LOG_FILE"
fi

echo "⏳ [2/4] Attente de Keycloak sur ${KEYCLOAK_URL} …"
for i in {1..60}; do
    if curl -fsS "$KEYCLOAK_URL" >/dev/null 2>&1; then
        echo "   Keycloak OK (realm demo disponible)."
        break
    fi
    sleep 2
    if [[ $i -eq 60 ]]; then
        echo "❌ Keycloak n'a pas démarré après 120 s — voir '$KEYCLOAK_LOG_HINT'." >&2
        exit 1
    fi
done

echo "🔨 [3/4] Build Maven (clean package, sans tests)…"
mvn -q -DskipTests clean package

echo "🚀 [4/4] Lancement des 3 services en arrière-plan…"
nohup mvn -pl resource-server  spring-boot:run >"$LOG_DIR/resource-server.log"  2>&1 &
echo "   resource-server  (:8081) PID=$! — log: $LOG_DIR/resource-server.log"
nohup mvn -pl client-service   spring-boot:run >"$LOG_DIR/client-service.log"   2>&1 &
echo "   client-service   (:8082) PID=$! — log: $LOG_DIR/client-service.log"
nohup mvn -pl frontend-service spring-boot:run >"$LOG_DIR/frontend-service.log" 2>&1 &
echo "   frontend-service (:8083) PID=$! — log: $LOG_DIR/frontend-service.log"

cat <<EOF

✅ Démo lancée.

   • Keycloak ($KEYCLOAK_MODE) : http://localhost:8080  (admin / admin)
   • Frontend       : http://localhost:8083  (alice/alice, bob/bob, demo/demo)
   • Resource API   : http://localhost:8081/api/public/hello
   • Client M2M     : http://localhost:8082/client/call

   Logs : tail -f $LOG_DIR/{resource-server,client-service,frontend-service}.log
   Stop : ./start-all.sh stop --$KEYCLOAK_MODE
EOF
