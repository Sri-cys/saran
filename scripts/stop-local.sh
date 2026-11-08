#!/usr/bin/env bash
# ==============================================================================
# PSG iTech Library - Localhost Shutdown Automation Script
# Stops only the project's configured Apache Tomcat 9 instance safely.
# ==============================================================================
set -euo pipefail

TOMCAT_CANDIDATES=(
    "${CATALINA_HOME:-}"
    "/opt/homebrew/opt/tomcat@9/libexec"
    "/usr/local/opt/tomcat@9/libexec"
    "/opt/homebrew/Cellar/tomcat@9"/*
    "/usr/local/Cellar/tomcat@9"/*
    "${HOME}/Downloads/apache-tomcat-9"*
)

TOMCAT_DIR=""
for candidate in "${TOMCAT_CANDIDATES[@]}"; do
    if [[ -n "${candidate}" && -d "${candidate}" && -f "${candidate}/bin/catalina.sh" ]]; then
        TOMCAT_DIR="${candidate}"
        break
    fi
done

echo "=============================================================================="
echo "Stopping PSG iTech Library System..."
echo "=============================================================================="

if [[ -n "${TOMCAT_DIR}" && -f "${TOMCAT_DIR}/bin/shutdown.sh" ]]; then
    echo "Invoking Tomcat shutdown (${TOMCAT_DIR}/bin/shutdown.sh)..."
    "${TOMCAT_DIR}/bin/shutdown.sh" 5 -force >/dev/null 2>&1 || true
    sleep 2
fi

# Clean up any remaining process on ports 8081 / 8005
REMAINING_PIDS=$(lsof -t -i :8081 -i :8005 2>/dev/null || true)
if [[ -n "${REMAINING_PIDS}" ]]; then
    echo "Releasing remaining port listeners (PID: ${REMAINING_PIDS})..."
    kill -9 ${REMAINING_PIDS} >/dev/null 2>&1 || true
    sleep 1
fi

echo "SUCCESS: PSG iTech Library Tomcat instance stopped. Ports 8081 and 8005 released."

