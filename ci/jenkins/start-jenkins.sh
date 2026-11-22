#!/usr/bin/env bash
# Starts a local Jenkins for this project only. Everything lives in tools/jenkins/ (git-ignored):
#   jenkins.war, home/ (JENKINS_HOME with plugins and builds), admin-password.
# One-time download: see docs/guide/11-jenkins-pipeline.md.  Open http://127.0.0.1:8081 (user: admin).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/tools/jenkins"
[[ -f "$DIR/jenkins.war" ]] || { echo "Missing $DIR/jenkins.war - see docs/guide/11-jenkins-pipeline.md" >&2; exit 1; }

# Admin password: generated once, readable only by you, never in git.
if [[ ! -f "$DIR/admin-password" ]]; then
  (umask 077; openssl rand -base64 18 > "$DIR/admin-password")
fi
export JENKINS_ADMIN_PASSWORD="$(cat "$DIR/admin-password")"
export JENKINS_HOME="$DIR/home"
# Folder that holds mvn on THIS machine (Apple Silicon: /opt/homebrew/bin, Intel Mac: /usr/local/bin, Linux: /usr/bin...)
MVN="$(command -v mvn)" || { echo "mvn is not on PATH - install Maven first" >&2; exit 1; }
export TOOLS_PATH="${TOOLS_PATH:-$(dirname "$MVN")}"
export CASC_JENKINS_CONFIG="$ROOT/ci/jenkins/jenkins.yaml"
export REPO_URL="${REPO_URL:-file://$ROOT}"   # local repo by default; REPO_URL=https://github.com/... for the remote

# - runSetupWizard=false: JCasC configures everything
# - ALLOW_LOCAL_CHECKOUT: allows the file:// repository (local machine only)
# - DirectoryBrowserSupport.CSP: Jenkins blocks scripts/styles in archived HTML by default, which breaks the Extent
#   report. Relaxed for this local, single-user Jenkins only - never on a shared server.
exec java \
  -Djenkins.install.runSetupWizard=false \
  -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true \
  "-Dhudson.model.DirectoryBrowserSupport.CSP=sandbox allow-scripts allow-same-origin; default-src 'self' 'unsafe-inline' data: https:; img-src 'self' data: https:;" \
  -jar "$DIR/jenkins.war" --httpListenAddress=127.0.0.1 --httpPort=8081
