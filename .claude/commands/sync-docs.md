# Sync Docs — jfitVLMC

Deep-analyzes the codebase and updates CLAUDE.md to accurately reflect the current state of the code.

## Input

$ARGUMENTS

Optional arguments:
- `--dry-run` — show what would change without modifying files
- `--scope fitvlmc` — only analyze core package
- `--scope vlmc` — only analyze VLMC data structures
- `--scope suffixarray` — only analyze suffix array algorithms

If no arguments, update ALL documentation.

## Procedure

### Phase 1: Deep Codebase Analysis

1. **Discover structure**: Use Glob to map all `.java` files under `src/`
2. **Extract public API**: Find all public classes and their public methods
3. **Map dependencies**: Identify imports between packages (`fitvlmc`, `vlmc`, `suffixarray`, `ECFEntity`)
4. **Find tests**: Locate test files under `src/test/` and understand coverage
5. **Check CLI options**: Extract all `LongOpt` definitions from `fitVlmc.java`
6. **Check REST endpoints**: Extract HTTP handlers from `RESTVlmc.java`
7. **Check pom.xml**: Extract dependencies, plugins, Java version

Key patterns to extract:
- `public class` / `public interface` — API surface
- `new LongOpt(` — CLI options
- `server.createContext(` — REST endpoints
- `<dependency>` in pom.xml — external dependencies
- `public static void main(` — entry points

### Phase 2: Gap Analysis

Compare CLAUDE.md content against the actual codebase:

1. **Read CLAUDE.md**
2. **Identify gaps**: classes in code not documented, documented classes that no longer exist
3. **Check accuracy**: file paths, class names, CLI options, package structure
4. **Detect stale content**: references to deleted files, removed features, wrong descriptions

### Phase 3: Update CLAUDE.md

Update with accurate, comprehensive content. Follow this structure:

```markdown
# CLAUDE.md — jfitVLMC Project

## Panoramica
One paragraph describing the project.

## Java Environment (CRITICAL)
Java version, Maven version, build commands.

## Dipendenza ECF (IMPORTANTE)
ECF dependency info and installation.

## Struttura Progetto
```tree``` showing directory layout with one-line descriptions.

## Componenti Chiave
Table: | Classe | Responsabilita |

## CLI Reference
All CLI options with examples.

## Git Workflow
Branch strategy, commit convention, CI pipeline.

## Convenzioni Codice
Naming, packages, error handling, libraries.

## Comandi Rapidi
Build, test, run commands.
```

**Rules for updating:**
- Preserve existing sections that are still accurate
- Add new classes/files that appeared since last sync
- Remove references to deleted classes/files
- Update CLI options if they changed
- Update dependency list if pom.xml changed
- Keep the structure consistent with the template above

### Phase 4: Commit

After updating:

```bash
git add CLAUDE.md
git commit -m "$(cat <<'EOF'
docs: sync CLAUDE.md with current codebase state

Updated to reflect:
- <list of major changes>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
git push origin $(git branch --show-current)
```

### Phase 5: Report

Print a summary:

```
===================================================
  DOCS SYNC REPORT
===================================================

  Files updated:
    CLAUDE.md — <what changed>

  Gaps found and fixed: N
  Sections unchanged: M (already accurate)
===================================================
```

## Rules

1. **Never invent** — only document what exists in the code
2. **Read before writing** — always read the source file before documenting it
3. **English for code references, Italian for descriptions** — match CLAUDE.md convention
4. **No opinions** — document behavior, not preferences
5. **Include file references** — when describing key classes, reference the file path
6. **Keep CLAUDE.md practical** — it should help a developer (or AI) work with the code immediately
