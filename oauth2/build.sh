#!/bin/bash
set -e

# Configuration
IMAGE_REPO="prez-oauth2"
TAG="latest"
DEFAULT_MODULES=("resource-server" "client-service" "frontend-htmx-service" "frontend-vue-service")

# Détection de Maven
if [ -f "./mvnw" ]; then
    MVN="./mvnw"
else
    MVN="mvn"
fi

usage() {
    echo "Usage: $0 [--docker [options]] [module...]"
    echo "Options:"
    echo "  -d, --docker    Construire les images Docker au lieu de compiler"
    echo "  -n, --native    Construire des images natives (avec --docker, nécessite GraalVM)"
    echo "  -t, --tag <tag> Tag de l'image (avec --docker, défaut : $TAG)"
    echo "  -h, --help      Afficher cette aide"
    echo ""
    echo "Sans --docker, le projet est compilé. Avec --docker, les images sont construites."
    echo "Si aucun module n'est spécifié, tous les modules sont concernés : ${DEFAULT_MODULES[*]}"
    exit ${1:-1}
}

DOCKER=false
NATIVE=false
TAG_SPECIFIED=false
SELECTED_MODULES=()

while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--docker)
            DOCKER=true
            shift
            ;;
        -n|--native)
            NATIVE=true
            shift
            ;;
        -t|--tag)
            TAG="$2"
            TAG_SPECIFIED=true
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

if [ "$DOCKER" = false ]; then
    if [ "$NATIVE" = true ] || [ "$TAG_SPECIFIED" = true ]; then
        echo "Les options --native et --tag nécessitent l'option --docker."
        usage
    fi

    if [ ${#SELECTED_MODULES[@]} -eq 0 ]; then
        ARGS=("-DskipTests" "clean" "compile")
    else
        MODULE_SELECTOR=$(IFS=,; printf '%s' "${SELECTED_MODULES[*]}")
        ARGS=("-pl" "$MODULE_SELECTOR" "-am" "-DskipTests" "clean" "compile")
    fi

    echo "=== Compilation de prez-oauth2 ==="
    if [ ${#SELECTED_MODULES[@]} -eq 0 ]; then
        echo "Modules: tous"
    else
        echo "Modules: ${SELECTED_MODULES[*]}"
    fi
    echo "---"
    echo "Exécution : $MVN ${ARGS[*]}"
    "$MVN" "${ARGS[@]}"
    echo "Compilation terminée avec succès."
    exit 0
fi

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
    "$MVN" "${ARGS[@]}"
    
    echo "✅ Image créée : $IMAGE_NAME"
    echo "---"
done

echo "Toutes les images ont été construites avec succès."
