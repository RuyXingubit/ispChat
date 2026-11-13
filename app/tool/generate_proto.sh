#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROTO_DIR="${APP_DIR}/../node/ispchat-common/src/main/proto"
PROTO_FILE="${PROTO_DIR}/ispchat.proto"
OUT_DIR="${APP_DIR}/lib/core/grpc/generated"
PROTOC_PLUGIN_VERSION="25.0.0"

if [[ ! -f "${PROTO_FILE}" ]]; then
  echo "Erro: arquivo proto nao encontrado em ${PROTO_FILE}" >&2
  exit 1
fi

export PATH="${HOME}/.pub-cache/bin:${PATH}"

find_protoc() {
  if [[ -n "${PROTOC:-}" && -x "${PROTOC}" ]]; then
    echo "${PROTOC}"
    return 0
  fi
  if command -v protoc >/dev/null 2>&1; then
    command -v protoc
    return 0
  fi
  local arch os_tag gradle_candidate
  arch="$(uname -m)"
  case "$(uname -s)" in
    Darwin)
      if [[ "${arch}" == "arm64" ]]; then
        os_tag="osx-aarch_64"
      else
        os_tag="osx-x86_64"
      fi
      ;;
    Linux)
      if [[ "${arch}" == "aarch64" ]]; then
        os_tag="linux-aarch_64"
      else
        os_tag="linux-x86_64"
      fi
      ;;
    *)
      os_tag=""
      ;;
  esac
  if [[ -n "${os_tag}" && -d "${HOME}/.gradle/caches" ]]; then
    gradle_candidate="$(find "${HOME}/.gradle/caches" -type f -name "protoc-[0-9]*-${os_tag}.exe" 2>/dev/null | sort -V | tail -n 1 || true)"
    if [[ -n "${gradle_candidate}" ]]; then
      chmod +x "${gradle_candidate}" 2>/dev/null || true
      if [[ -x "${gradle_candidate}" ]]; then
        echo "${gradle_candidate}"
        return 0
      fi
    fi
  fi
  return 1
}

PROTOC_BIN="$(find_protoc || true)"
if [[ -z "${PROTOC_BIN}" ]]; then
  echo "Erro: compilador protoc nao encontrado no PATH nem no cache do Gradle. Defina PROTOC=/caminho/protoc ou instale protobuf." >&2
  exit 1
fi

dart pub global activate protoc_plugin "${PROTOC_PLUGIN_VERSION}"

PLUGIN_BIN="${HOME}/.pub-cache/bin/protoc-gen-dart"
if [[ ! -x "${PLUGIN_BIN}" ]]; then
  echo "Erro: protoc-gen-dart nao encontrado em ${PLUGIN_BIN}" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
rm -f "${OUT_DIR}"/*.dart

"${PROTOC_BIN}" \
  --plugin="protoc-gen-dart=${PLUGIN_BIN}" \
  --dart_out="grpc:${OUT_DIR}" \
  -I "${PROTO_DIR}" \
  "${PROTO_FILE}"

echo "Stubs Dart gerados com sucesso em ${OUT_DIR} usando protoc_plugin ${PROTOC_PLUGIN_VERSION}."
