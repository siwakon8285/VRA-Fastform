# VRA-Fastform Agent Instructions

Before modifying this repository, inspect the current repository state and
read the project documents relevant to the task.

## Authority Order

When instructions differ, use this precedence:

1. Explicit current user-approved decision
2. Canonical VRA project documentation
   - docs/PRODUCT.md
   - docs/DESIGN.md
   - docs/SECURITY.md
   - docs/TESTING.md
   - docs/OPERATIONS.md
   - docs/ROADMAP.md
   - docs/BRANCH_PLAN.md
3. Accepted ADRs under docs/adr/
4. Current frozen POC specification and approved implementation plan
5. Generic engineering guides under docs/engineering/agent-guides/

Generic engineering guides must not silently override a VRA-specific
architecture decision, accepted ADR, frozen specification, or explicit
user-approved decision.

If authoritative sources conflict:
STOP and report the contradiction before implementation.

## Required Engineering Guides

Backend work:
- docs/engineering/agent-guides/BACKEND.md

Database / persistence / migrations:
- docs/engineering/agent-guides/DATABASE.md

Frontend / Web:
- docs/engineering/agent-guides/FRONTEND.md

Mobile:
- docs/engineering/agent-guides/MOBILE.md

Cross-cutting work:
Read every relevant guide.

## Core Working Rules

- Plan First.
- Security before correctness shortcuts or convenience.
- Preserve accepted architecture unless evidence justifies a reviewed change.
- Do not invent requirements.
- Do not silently resolve contradictions.
- Use authoritative state and failure evidence for critical claims.
- Prefer the smallest justified change.
- Do not broaden privileges to make implementation easier.
- Do not claim a gate, test, CI run, deployment, or production property without evidence.

## Git

The user controls Git mutations.

Do not autonomously run:

- git add
- git commit
- git push
- git pull
- git merge
- git rebase
- git reset
- git stash
- git switch
- git checkout

unless the user explicitly requests that exact mutation.

Preferred flow:

implementation
→ tests/evidence
→ review
→ user staging
→ staged review
→ user commit
→ commit-integrity check

## Shell

Primary interactive environment:

- macOS
- zsh
- Spaceship prompt

Do not persistently enable `set -euo pipefail` in the interactive shell.
Strict shell mode is acceptable inside standalone scripts executed with bash.