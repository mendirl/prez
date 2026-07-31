#!/bin/bash
set -e

# Configuration
IMAGE_REPO="prez-oauth2"
TAG="latest"
DEFAULT_MODULES=("resource-server" "client-service" "frontend-service")

# Détection de Maven
if [ -f "./mvnw" ]; then
    MVN="./mvnw"
else
    MVN="mvn"
fi

usage() {
    echo "Usage: $0 [options] [module...]"
    echo "Options:"
    echo "  -n, --native    Build images natives (long, nécessite GraalVM)"
    echo "  -t, --tag <tag> Tag de l'image (défaut : $TAG)"
    echo "  -h, --help      Afficher cette aide"
    echo ""
    echo "Si aucun module n'est spécifié, tous les modules seront buildés : ${DEFAULT_MODULES[*]}"
    exit ${1:-1}
}

NATIVE=false
SELECTED_MODULES=()

while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--native)
            NATIVE=true
            shift
            ;;
        -t|--tag)
            TAG="$2"
            shift 2
            ;;
        -h|--help)
            usage 0
            ;;
        -*)
            echo "Option inconnue: $1"
            usage
            ;;
        *)
            SELECTED_MODULES+=("$1")
            shift
            ;;
    esac
done

if [ ${#SELECTED_MODULES[@]} -eq 0 ]; then
    SELECTED_MODULES=("${DEFAULT_MODULES[@]}")
fi

echo "=== Construction des images Docker pour prez-oauth2 ==="
echo "Tag    : $TAG"
echo "Native : $NATIVE"
echo "Modules: ${SELECTED_MODULES[*]}"
echo "---"

for module in "${SELECTED_MODULES[@]}"; do
    echo "🔨 Construction de l'image pour '$module'..."
    
    # Construction du nom de l'image
    IMAGE_NAME="$IMAGE_REPO/$module:$TAG"
    
    # Paramètres Maven
    # On utilise -pl et -am pour builder le module et ses dépendances
    ARGS=("-pl" "$module" "-am" "spring-boot:build-image" "-DskipTests" "-Dspring-boot.build-image.imageName=$IMAGE_NAME")
    
    if [ "$NATIVE" = true ]; then
        ARGS+=("-Pnative")
    fi
    
    echo "Exécution : $MVN ${ARGS[*]}"
    $MVN "${ARGS[@]}"
    
    echo "✅ Image créée : $IMAGE_NAME"
    echo "---"
done

echo "Toutes les images ont été construites avec succès."
