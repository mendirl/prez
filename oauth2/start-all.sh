#!/usr/bin/env bash
#
# start-all.sh — Relance complète de la démo OAuth2/OIDC.
#
# Étapes :
#   1. Arrête Keycloak et supprime le volume (force la réimportation du realm
#      `keycloak/realm-demo.json` : rôles ADMIN/USER, clients, users alice/bob/demo).
#   2. Redémarre Keycloak et attend que le realm `demo` réponde.
#   3. Build complet des 3 modules (sans tests).
#   4. Lance les 3 services Spring Boot en arrière-plan, logs dans /tmp/*.log.
#
# Usage :
#   ./start-all.sh          # tout (re)lancer
#   ./start-all.sh stop     # tout arrêter (services + Keycloak + volume)
#
# Prérequis : docker, docker compose, mvn, curl, jq (optionnel).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

KEYCLOAK_URL="http://localhost:8080/realms/demo"
LOG_DIR="/tmp"

stop_all() {
    echo "🛑 Arrêt des services Spring Boot…"
    pkill -f spring-boot:run 2>/dev/null || true
    echo "🛑 Arrêt de Keycloak (avec suppression du volume)…"
    docker compose down -v
    echo "✅ Tout est arrêté."
}

if [[ "${1:-}" == "stop" ]]; then
    stop_all
    exit 0
fi

echo "🧹 [1/4] Réinitialisation de Keycloak (down -v pour réimporter le realm)…"
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d

echo "⏳ [2/4] Attente de Keycloak sur ${KEYCLOAK_URL} …"
for i in {1..60}; do
    if curl -fsS "$KEYCLOAK_URL" >/dev/null 2>&1; then
        echo "   Keycloak OK (realm demo disponible)."
        break
    fi
    sleep 2
    if [[ $i -eq 60 ]]; then
        echo "❌ Keycloak n'a pas démarré après 120 s — voir 'docker logs keycloak'." >&2
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

   • Keycloak admin : http://localhost:8080  (admin / admin)
   • Frontend       : http://localhost:8083  (alice/alice, bob/bob, demo/demo)
   • Resource API   : http://localhost:8081/api/public/hello
   • Client M2M     : http://localhost:8082/client/call

   Logs : tail -f $LOG_DIR/{resource-server,client-service,frontend-service}.log
   Stop : ./start-all.sh stop
EOF
