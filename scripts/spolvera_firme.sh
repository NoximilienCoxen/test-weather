#!/usr/bin/env bash
#
# Toglie dalla storia le firme degli strumenti: i trailer di paternita'
# automatica, i link alle sessioni, e l'autore "Claude".
#
# **E' una riscrittura della storia.** Ogni commit toccato cambia SHA. Sul
# branch predefinito questo vuol dire che chi ha un clone deve riallinearsi a
# mano, e che i link a commit vecchi - il corpo della release apk-latest, gli
# INFO.txt su ci-artifacts - puntano nel vuoto. Il contenuto dei file non viene
# toccato: lo script lo verifica da solo alla fine e si ferma se non e' vero.
#
# Cosa NON tocca, e apposta: i nomi dei branch citati dai commit di merge
# ("Merge pull request #5 from NoximilienCoxen/claude/..."), e i percorsi di
# file che esistono davvero (`.claude/settings.local.json`). Quelli non sono
# firme: sono descrizioni di cose reali, e riscriverle farebbe raccontare alla
# storia dei branch mai esistiti. Su main ne restano una dozzina, tutte di
# questo tipo. Per farle sparire davvero servirebbe rinominare i branch e la
# cartella di configurazione, che e' una decisione diversa e piu' larga.
#
#   uso:  bash scripts/spolvera_firme.sh <branch>
#   es.:  bash scripts/spolvera_firme.sh main
#
# Dopo, e solo dopo aver guardato il risultato:
#   git push --force-with-lease origin <branch>
#
# Se qualcosa va storto, il ref di salvataggio riporta tutto indietro:
#   git reset --hard refs/backup/prima-di-spolverare
set -euo pipefail

BRANCH="${1:?serve il nome del branch, per esempio: main}"

FIRMA_NOME="NoximilienCoxen"
FIRMA_EMAIL="313902161+NoximilienCoxen@users.noreply.github.com"

git rev-parse --verify "$BRANCH" >/dev/null 2>&1 || {
  echo "il branch '$BRANCH' non esiste"; exit 1
}

# filter-branch si rifiuta di lavorare con modifiche non committate, ma lo dice
# **dopo** aver gia' cominciato. Meglio fermarsi prima, con un motivo leggibile.
if [ -n "$(git status --porcelain)" ]; then
  echo "ci sono modifiche non committate: committale o mettile da parte prima."
  git status --short
  exit 1
fi

echo "== salvo dove siamo, prima di toccare qualsiasi cosa =="
git update-ref "refs/backup/prima-di-spolverare" "$BRANCH"
PRIMA=$(git rev-parse "$BRANCH")
echo "   $PRIMA  -> refs/backup/prima-di-spolverare"

echo "== quanto c'e' da spolverare =="
echo "   commit:            $(git rev-list --count "$BRANCH")"
echo "   con autore Claude: $(git rev-list --count --author='Claude' "$BRANCH")"

FILTRO=$(mktemp)
cat > "$FILTRO" <<'FILTER'
sed -e '/^Co-Authored-By: Claude/d' \
    -e '/^Claude-Session:/d' \
    -e '/^Generated with \[Claude Code\]/d' \
    -e '/^🤖 Generated with \[Claude Code\]/d' \
    -e '/^https:\/\/claude\.ai\/code\/session_/d' \
  | awk 'BEGIN{n=0} {r[n++]=$0} END{ while(n>0 && r[n-1] ~ /^[[:space:]]*$/) n--; for(i=0;i<n;i++) print r[i] }'
FILTER
chmod +x "$FILTRO"

echo "== riscrivo =="
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f \
  --env-filter "
    export GIT_AUTHOR_NAME='$FIRMA_NOME'
    export GIT_AUTHOR_EMAIL='$FIRMA_EMAIL'
    export GIT_COMMITTER_NAME='$FIRMA_NOME'
    export GIT_COMMITTER_EMAIL='$FIRMA_EMAIL'
  " \
  --msg-filter "$FILTRO" \
  -- "$BRANCH"

rm -f "$FILTRO"

echo "== controllo =="
RESIDUI=$(git log --format='%B' "$BRANCH" | grep -ci 'claude\|anthropic' || true)
echo "   riferimenti rimasti nei messaggi: $RESIDUI"
echo "   autori: $(git log --format='%an <%ae>' "$BRANCH" | sort -u | tr '\n' ' ')"

# La garanzia che conta: la storia cambia, i file no.
if [ -z "$(git diff "refs/backup/prima-di-spolverare" "$BRANCH")" ]; then
  echo "   contenuto: IDENTICO, nessuna riga di codice toccata"
else
  echo "   contenuto: DIVERSO — qualcosa non va, NON spingere"
  echo "   torna indietro con: git reset --hard refs/backup/prima-di-spolverare"
  exit 1
fi

echo
echo "Fatto. Guarda il risultato con:  git log --format='%h %an | %s' $BRANCH | head"
echo "Poi, se convince:                git push --force-with-lease origin $BRANCH"
