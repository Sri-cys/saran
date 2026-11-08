#!/usr/bin/env bash
# ==============================================================================
# PSG iTech Library - Localhost Startup & Deployment Automation Script
# Compatible with macOS, Eclipse Temurin JDK 17/25, Apache Tomcat 9, MySQL 8
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "${SCRIPT_DIR}/../pom.xml" && -d "${SCRIPT_DIR}/../src" ]]; then
    PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
elif [[ -d "${SCRIPT_DIR}/../Library-Management" ]]; then
    PROJECT_DIR="$(cd "${SCRIPT_DIR}/../Library-Management" && pwd)"
else
    PROJECT_DIR="${SCRIPT_DIR}"
fi

echo "=============================================================================="
echo "PSG iTech Library - Localhost Deployment Orchestrator"
echo "Project Directory: ${PROJECT_DIR}"
echo "=============================================================================="

# 1. Verify Java Installation
echo "[1/8] Verifying Java Environment..."
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java is not installed or not available on PATH." >&2
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | head -n 1)
echo "  Java runtime detected: ${JAVA_VER}"

# 2. Verify Maven Installation
echo "[2/8] Verifying Apache Maven..."
if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven ('mvn') is not found on PATH." >&2
    exit 1
fi
MVN_VER=$(mvn -v | head -n 1)
echo "  Maven detected: ${MVN_VER}"

# 3. Detect and Validate Apache Tomcat 9 Installation (Legacy javax.servlet namespace)
echo "[3/8] Locating Apache Tomcat 9..."
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

if [[ -z "${TOMCAT_DIR}" ]]; then
    echo "ERROR: Apache Tomcat 9 installation not found." >&2
    echo "Install it via Homebrew: brew install tomcat@9" >&2
    exit 1
fi
export CATALINA_HOME="${TOMCAT_DIR}"
export CATALINA_BASE="${TOMCAT_DIR}"
echo "  Using Tomcat 9 at: ${CATALINA_HOME}"

# Ensure connector port 8081 is configured in server.xml
SERVER_XML="${CATALINA_HOME}/conf/server.xml"
if [[ -f "${SERVER_XML}" ]]; then
    if grep -q 'Connector port="8080"' "${SERVER_XML}"; then
        echo "  Configuring Tomcat connector port from 8080 to 8081 in server.xml..."
        sed -i '' 's/Connector port="8080"/Connector port="8081"/' "${SERVER_XML}" || true
    fi
fi

# Ensure mysql-connector-j is present in Tomcat lib
if ! ls "${CATALINA_HOME}/lib"/mysql* >/dev/null 2>&1; then
    M2_MYSQL_JAR=$(find "${HOME}/.m2/repository" -name "mysql-connector-j-8*.jar" 2>/dev/null | head -n 1 || true)
    if [[ -n "${M2_MYSQL_JAR}" && -f "${M2_MYSQL_JAR}" ]]; then
        echo "  Copying MySQL connector jar to Tomcat lib..."
        cp "${M2_MYSQL_JAR}" "${CATALINA_HOME}/lib/" || true
    fi
fi

# 4. Verify / Start MySQL Database
echo "[4/8] Checking MySQL Database Service..."
if ! mysqladmin ping -u root --silent >/dev/null 2>&1; then
    echo "  MySQL service is not running. Attempting to start via Homebrew..."
    brew services start mysql || true
    sleep 3
    if ! mysqladmin ping -u root --silent >/dev/null 2>&1; then
        echo "WARNING: Could not connect to MySQL with 'mysqladmin ping'. Verifying if port 3306 is open..."
        if ! nc -z 127.0.0.1 3306 >/dev/null 2>&1; then
            echo "ERROR: MySQL is not responding on 127.0.0.1:3306." >&2
            echo "Please run: brew services start mysql" >&2
            exit 1
        fi
    fi
fi
echo "  MySQL service is active and responsive."

# 5. Build and Test Maven Project
echo "[5/8] Building and Testing Maven Project..."
cd "${PROJECT_DIR}"
mvn clean test
echo "  All automated tests executed successfully!"

mvn package -DskipTests
WAR_PATH="${PROJECT_DIR}/target/psgitech-library.war"
if [[ ! -f "${WAR_PATH}" ]]; then
    echo "ERROR: WAR file was not generated at: ${WAR_PATH}" >&2
    exit 1
fi
echo "  Generated deployable WAR: $(ls -lh "${WAR_PATH}" | awk '{print $9, "(" $5 ")"}')"

# 6. Stop Existing Tomcat on ports 8081 / 8005 safely
echo "[6/8] Checking Port Availability (8081, 8005)..."
CONFLICTING_PIDS=$(lsof -t -i :8081 -i :8005 2>/dev/null || true)
if [[ -n "${CONFLICTING_PIDS}" ]]; then
    echo "  Stopping process(es) holding ports 8081 / 8005 (PID: ${CONFLICTING_PIDS})..."
    "${CATALINA_HOME}/bin/shutdown.sh" 5 -force >/dev/null 2>&1 || true
    sleep 2
    REMAINING_PIDS=$(lsof -t -i :8081 -i :8005 2>/dev/null || true)
    if [[ -n "${REMAINING_PIDS}" ]]; then
        echo "  Terminating remaining Tomcat process(es) holding ports: ${REMAINING_PIDS}"
        kill -9 ${REMAINING_PIDS} >/dev/null 2>&1 || true
        sleep 1
    fi
fi
echo "  Ports 8081 and 8005 are free."

# 7. Deploy WAR to Tomcat 9
echo "[7/8] Deploying psgitech-library.war to Tomcat 9..."
rm -rf "${CATALINA_HOME}/webapps/psgitech-library"
rm -f "${CATALINA_HOME}/webapps/psgitech-library.war"
cp "${WAR_PATH}" "${CATALINA_HOME}/webapps/psgitech-library.war"

# Start Tomcat 9
echo "  Starting Tomcat 9 server..."
"${CATALINA_HOME}/bin/startup.sh"

# 8. Bounded Readiness Check
echo "[8/8] Performing Readiness Health-Check on http://localhost:8081/psgitech-library/login.html..."
TARGET_URL="http://localhost:8081/psgitech-library/login.html"
SUCCESS=false
MAX_ATTEMPTS=30

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
    STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${TARGET_URL}" || echo "000")
    if [[ "${STATUS_CODE}" == "200" ]]; then
        SUCCESS=true
        break
    fi
    sleep 1
done

if [[ "${SUCCESS}" == "true" ]]; then
    echo ""
    echo "=============================================================================="
    echo "SUCCESS: PSG iTech Library System successfully deployed and running!"
    echo "=============================================================================="
    echo "Application Home URL:  http://localhost:8081/psgitech-library/"
    echo "Login Portal URL:      http://localhost:8081/psgitech-library/login.html"
    echo "Notifications URL:     http://localhost:8081/psgitech-library/notifications.html"
    echo "Concurrency Monitor:   http://localhost:8081/psgitech-library/concurrency-monitor.html"
    echo "Tomcat Server Logs:    ${CATALINA_HOME}/logs/catalina.out"
    echo "=============================================================================="
    
    # Feature 11: Verify notification unread-count endpoint returns 401 for anonymous access
    NOTIF_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081/psgitech-library/notifications/unread-count" || echo "000")
    if [[ "${NOTIF_CODE}" == "401" ]]; then
        echo "  [Feature 11] Notification unread-count endpoint verified (401 Unauthorized for anonymous request)."
    else
        echo "  WARNING: Notification endpoint returned status: ${NOTIF_CODE} (expected 401)."
    fi
    
    if command -v open >/dev/null 2>&1; then
        open "${TARGET_URL}" || true
    fi
    exit 0
else
    echo ""
    echo "=============================================================================="
    echo "DEPLOYMENT FAILED: Application did not respond with HTTP 200 within ${MAX_ATTEMPTS}s"
    echo "HTTP Status Code received: ${STATUS_CODE}"
    echo "=============================================================================="
    echo "Recent Tomcat Logs (${CATALINA_HOME}/logs/catalina.out):"
    echo "------------------------------------------------------------------------------"
    tail -n 40 "${CATALINA_HOME}/logs/catalina.out" 2>/dev/null || true
    echo "------------------------------------------------------------------------------"
    exit 1
fi

