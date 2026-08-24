#!/usr/bin/env bash
# Pubblica il contenuto di /tmp/ciout sul branch ci-artifacts, dentro la
# sottocartella passata come primo argomento. Branch separato: cosi' la CI non
# entra mai in conflitto con il branch di sviluppo.
set -euo pipefail

SUBDIR="${1:?serve la sottocartella di destinazione}"
SRC="/tmp/ciout"
[ -d "$SRC" ] || { echo "niente da pubblicare in $SRC"; exit 0; }

REMOTE="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
WORK=$(mktemp -d)
cd "$WORK"

git init --quiet repo
cd repo
git remote add origin "$REMOTE"
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

# Un clone --depth 1 implicherebbe --single-branch e lascerebbe
# origin/ci-artifacts inesistente: parto da un init e prendo solo cio' che serve.
if git ls-remote --exit-code --heads origin ci-artifacts >/dev/null 2>&1; then
  git fetch --quiet --depth 1 origin ci-artifacts
  git checkout --quiet -B ci-artifacts FETCH_HEAD
else
  git checkout --quiet --orphan ci-artifacts
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
  if git push --quiet origin ci-artifacts 2>/dev/null; then
    echo "pubblicato in ci-artifacts/$SUBDIR"
    exit 0
  fi
  echo "push respinta, tentativo $attempt: riallineo e riprovo"
  sleep $((attempt * 3))
  git fetch --quiet --depth 1 origin ci-artifacts || true
  git rebase --quiet FETCH_HEAD || { git rebase --abort || true; }
done
echo "impossibile pubblicare dopo 4 tentativi" >&2
exit 1
