#!/usr/bin/env bash
# Pubblica il contenuto di /tmp/ciout sul branch ci-artifacts, dentro la
# sottocartella passata come primo argomento. Branch separato: cosi' la CI non
# entra mai in conflitto con il branch di sviluppo.
set -euo pipefail

SUBDIR="${1:?serve la sottocartella di destinazione}"
SRC="/tmp/ciout"
[ -d "$SRC" ] || { echo "niente da pubblicare in $SRC"; exit 0; }

WORK=$(mktemp -d)
git clone --quiet --depth 1 \
  "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" \
  "$WORK/repo"
cd "$WORK/repo"
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

if git ls-remote --exit-code --heads origin ci-artifacts >/dev/null 2>&1; then
  git fetch --quiet --depth 1 origin ci-artifacts
  git checkout --quiet -B ci-artifacts origin/ci-artifacts
else
  git checkout --quiet --orphan ci-artifacts
  git rm -rqf . 2>/dev/null || true
fi

rm -rf "${SUBDIR:?}"
mkdir -p "$SUBDIR"
cp -r "$SRC"/. "$SUBDIR"/

{
  echo "commit:  ${GITHUB_SHA}"
  echo "run:     ${GITHUB_RUN_ID}"
  echo "job:     ${SUBDIR}"
  echo "quando:  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$SUBDIR/INFO.txt"

git add -A
if git diff --cached --quiet; then
  echo "nessuna modifica da pubblicare"
  exit 0
fi
git commit --quiet -m "ci($SUBDIR): output run ${GITHUB_RUN_ID}"

for attempt in 1 2 3 4; do
  if git push --quiet origin ci-artifacts; then
    echo "pubblicato in ci-artifacts/$SUBDIR"
    exit 0
  fi
  echo "push fallito, tentativo $attempt"
  sleep $((attempt * 3))
  git fetch --quiet origin ci-artifacts && git rebase --quiet origin/ci-artifacts || true
done
echo "impossibile pubblicare dopo 4 tentativi" >&2
exit 1
