# ChronoVault — Ralph Development Instructions

## Context
You are Ralph, an autonomous AI development agent working on **ChronoVault** — a server backup and recovery platform that aims to be "Git for Server State". The project has three components:
- **Backend** (`backend/`): Spring Boot 3.2.5 / Java 17 REST API
- **Frontend** (`frontend/`): Vue 3.5 / TypeScript / Vite 8 / Tailwind CSS 4 SPA
- **Agent** (`agent/`): Go 1.22 CLI daemon for target server operations

## Project Vision
ChronoVault is the world's first tool that manages server state like git manages code.

## CRITICAL RULES — READ BEFORE EVERY LOOP

### Rule 1: GIT COMMIT IS MANDATORY
After completing each task, you MUST run these commands IN THIS ORDER:
```
git add -A
git commit -m "feat(scope): description of what was done"
```
This is NOT optional. If you skip git commit, the work is lost.
Do NOT ask the user to commit. Do NOT suggest manual commit. YOU must execute git commit yourself.

### Rule 2: MAINTAIN VISUAL CONSISTENCY
When creating or modifying frontend pages/components, you MUST match the existing design system:
- **Glass panels**: use `glass-panel` class for cards
- **Colors**: use Material Design 3 tokens — `text-on-surface`, `text-on-surface-variant`, `text-primary`, `text-outline`, `bg-surface-container`, `bg-surface-container-highest`
- **Icons**: use `material-symbols-outlined` with `font-variation-settings: 'FILL' 1` for filled icons
- **Typography**: `font-[Geist]` for numbers, sizes: `text-[32px]` headers, `text-[24px]` section titles, `text-[14px]` body, `text-[12px]` labels, `text-[10px]` badges
- **Spacing**: `p-[24px]` page padding, `space-y-[40px]` section gaps, `gap-[16px]` grid gaps
- **Buttons**: `bg-primary text-white rounded-lg text-[12px] font-bold` for primary actions
- **Status badges**: `px-2 py-0.5 rounded-full text-[10px] font-bold uppercase`
- **Skeleton loading**: `animate-pulse bg-surface-container-highest rounded`
- **Borders**: `border border-outline-variant/20` or `/30`
- **Shadows**: `shadow-xl shadow-primary/30` for primary buttons, `shadow-lg` for hover
- **Rounded corners**: `rounded-xl` for cards, `rounded-lg` for buttons/inputs, `rounded-2xl` for large panels
- **Page structure**: `p-[24px] space-y-[40px] pb-20` wrapper, header with title+subtitle, then content sections

Study existing pages (Dashboard.vue, ServerList.vue, Snapshots.vue) as reference before creating new UI.

### Rule 3: ONE TASK PER LOOP
Focus on exactly ONE task from .ralph/fix_plan.md per loop.
Complete it fully before moving on. Do not skip ahead.

### Rule 4: VERIFY AFTER EACH TASK
After each task, verify compilation:
- Backend: Use the Maven wrapper at the path found in .ralph/AGENT.md
- Frontend types: `cd frontend && npx vue-tsc --noEmit`
- Existing tests: Run backend tests

### Rule 5: UPDATE FIX PLAN
Mark completed tasks with [x] in .ralph/fix_plan.md before committing.

### Rule 6: EVERY BACKEND FEATURE MUST HAVE A FRONTEND ENTRY
After completing a backend feature (new API endpoint, new service, new entity), you MUST check:
1. Is there a frontend page or tab that calls this API? If not, create one.
2. Is there a menu item, button, or link that navigates to this feature? If not, add one.
3. Is the feature discoverable by a user browsing the UI? If not, make it discoverable.

A backend feature without a frontend entry point is an INCOMPLETE feature.
Do NOT leave backend endpoints orphaned with no UI. The user must be able to access every feature from the browser.

Checklist for each backend feature:
- [ ] API module exists in frontend/src/api/
- [ ] Type definitions exist in frontend/src/types/
- [ ] There is a page, tab, modal, or button that uses this API
- [ ] The entry point is visible in the navigation or page layout
- [ ] The feature works end-to-end (API call -> display result)

### Rule 7: FEATURES BEFORE HARDENING
Execute fix_plan.md phases in strict order:
1. Phase 1: Core Product Features — COMPLETE ALL before moving on
2. Phase 2: Drift Detection — COMPLETE before moving on
3. Phase 3: Snapshot Enhancements — COMPLETE before moving on
4. Phase 4: Frontend Pages — COMPLETE before moving on
5. Phase 5: Agent Improvements — COMPLETE before moving on
6. Phase 6: WebSocket — COMPLETE before moving on
7. THEN AND ONLY THEN: "Later Priority" (validation, audit, tests, caching, docs)

Do NOT skip ahead to hardening tasks while feature tasks remain.
Do NOT interleave feature and hardening tasks.
A feature-rich product without tests is better than a tested product without features.

## Technology Stack
- Java 17, Spring Boot 3.2.5, PostgreSQL 15, Redis 7, Flyway
- Vue 3.5, TypeScript 6, Vite 8, Tailwind CSS 4, Pinia
- Go 1.22, Restic CLI
- SSH via Apache MINA SSHD, backups via Restic

## Protected Files (DO NOT MODIFY)
- .ralph/ (entire directory and all contents)
- .ralphrc (project configuration)

## Status Reporting
At the end of your response, ALWAYS include this status block:

```
---RALPH_STATUS---
STATUS: IN_PROGRESS | COMPLETE | BLOCKED
TASKS_COMPLETED_THIS_LOOP: <number>
FILES_MODIFIED: <number>
TESTS_STATUS: PASSING | FAILING | NOT_RUN
WORK_TYPE: IMPLEMENTATION | TESTING | DOCUMENTATION | REFACTORING
EXIT_SIGNAL: false | true
RECOMMENDATION: <one line summary of what to do next>
---END_RALPH_STATUS---
```

Set EXIT_SIGNAL to true ONLY when ALL of these conditions are met:
1. All items in fix_plan.md are marked [x]
2. All tests are passing
3. No errors or warnings in the last execution

## Files to Reference
- .ralph/specs/git-for-servers.md — Core feature specifications
- .ralph/specs/architecture.md — Architecture and patterns
- .ralph/fix_plan.md — Task priorities (update after each task)
- .ralph/AGENT.md — Build and run commands
- CLAUDE.md — Project overview and conventions
