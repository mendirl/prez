#!/bin/bash
set -e

# Configuration par défaut
NAMESPACE="prez-oauth2"
RELEASE_NAME="prez-oauth2"
CHART_PATH="./helm/prez-oauth2"
KEYCLOAK_HOST="172.17.0.1"
INGRESS_HOST=""

# Aide
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -n, --namespace <ns>       Namespace Kubernetes (défaut : $NAMESPACE)"
    echo "  -k, --keycloak-host <ip>   IP de l'hôte pour Keycloak (défaut : $KEYCLOAK_HOST)"
    echo "  -i, --ingress-host <host>  Activer l'Ingress avec ce host (ex: prez-oauth2.local)"
    echo "  -h, --help                 Afficher cette aide"
    exit 1
}

# Parsing des arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        -k|--keycloak-host)
            KEYCLOAK_HOST="$2"
            shift 2
            ;;
        -i|--ingress-host)
            INGRESS_HOST="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Option inconnue: $1"
            usage
            ;;
    esac
done

echo "=== Déploiement de prez-oauth2 sur Kubernetes ==="
echo "Namespace      : $NAMESPACE"
echo "Keycloak Host  : $KEYCLOAK_HOST"
if [[ -n "$INGRESS_HOST" ]]; then
    echo "Ingress Host   : $INGRESS_HOST"
fi

# Vérification des prérequis (uniquement kubectl, helm est supposé présent chez l'utilisateur)
if ! command -v kubectl &> /dev/null; then
    echo "Erreur : kubectl n'est pas installé."
    exit 1
fi

if ! command -v helm &> /dev/null; then
    echo "Attention : helm n'est pas trouvé dans le PATH actuel."
    echo "Le script va tenter de l'exécuter quand même."
fi

# Création du namespace si nécessaire (idempotent)
echo "---"
echo "Vérification/Création du namespace '$NAMESPACE'..."
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Déploiement via Helm (upgrade --install est idempotent)
echo "---"
echo "Installation/Mise à jour du chart Helm (release: $RELEASE_NAME)..."

HELM_OPTS=(
    "--namespace" "$NAMESPACE"
    "--set" "keycloak.host=$KEYCLOAK_HOST"
)

if [[ -n "$INGRESS_HOST" ]]; then
    HELM_OPTS+=(
        "--set" "ingress.enabled=true"
        "--set" "ingress.host=$INGRESS_HOST"
    )
fi

helm upgrade --install "$RELEASE_NAME" "$CHART_PATH" "${HELM_OPTS[@]}" --wait

echo "---"
echo "Déploiement terminé avec succès."
echo "Pour voir les ressources : kubectl get all -n $NAMESPACE"
echo "---"
echo "Note : N'oubliez pas d'ajouter '127.0.0.1 keycloak' à votre fichier /etc/hosts"
if [[ -n "$INGRESS_HOST" ]]; then
    echo "et également '$INGRESS_HOST' pointant vers votre cluster (ex: 127.0.0.1)."
fi
echo "Si vous n'utilisez pas l'Ingress, faites un port-forward :"
echo "kubectl port-forward svc/frontend-service 8083:8083 -n $NAMESPACE"
