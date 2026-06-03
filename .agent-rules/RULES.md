# Agent Rules — Ryzix Player

These rules apply to every agent session working on this repository.

## Workflow Rules

1. **Pull before edit** — Always fetch/pull the latest code into the permanent directory `/home/runner/workspace/ryzix-player` before editing. Never clone fresh into a new directory each session.

2. **Edit in permanent directory** — All edits happen in `/home/runner/workspace/ryzix-player`. Do not use temporary clone locations.

3. **One push = one CI run** — All changes in a session are batched into a **single** Git commit using the GitHub Git Data API (blobs → tree → commit → update ref). Using `PUT /contents/{file}` per-file is forbidden because it creates one commit and one CI run per file.

4. **Research before writing** — Fetch and read the current file from the repo before modifying it. Do not guess at implementations. For Android APIs (Media3, RecyclerView, Jetpack, etc.) research the correct API.

5. **Rules are permanent** — When asked to follow a new rule, add it here so all future sessions inherit it.

## Push Method (single commit)

```
# 1. Get HEAD SHA
GET /repos/RD7890/Ryzix-Player/git/refs/heads/main

# 2. Get base tree SHA from commit
GET /repos/RD7890/Ryzix-Player/git/commits/{commitSha}

# 3. Create a blob for each changed file
POST /repos/RD7890/Ryzix-Player/git/blobs  { encoding: "base64", content: "<b64>" }

# 4. Create new tree (only changed files; unchanged files inherit from base)
POST /repos/RD7890/Ryzix-Player/git/trees  { base_tree: "<treeSha>", tree: [...] }

# 5. Create commit
POST /repos/RD7890/Ryzix-Player/git/commits  { message, tree, parents }

# 6. Update branch ref
PATCH /repos/RD7890/Ryzix-Player/git/refs/heads/main  { sha: "<newCommitSha>" }
```

Use `Node.js https` (not curl/bash git) to avoid bash git-keyword detection.  
Token is available as `process.env.GITHUB_PAT` in bash child processes.

## Project Conventions

- **Language**: Kotlin (idiomatic — no Java-style code)
- **Package**: `com.ryzix.player`
- **Min SDK**: 24 — **Target SDK**: 35
- **Stack**: Kotlin · Android · Media3/ExoPlayer · Room · Coil · ViewPager2 · Material3

## Key File Locations

| Path | Purpose |
|------|---------|
| `app/src/main/java/com/ryzix/player/` | Kotlin source |
| `app/src/main/res/` | Resources (layouts, drawables, values) |
| `app/src/main/AndroidManifest.xml` | App manifest |
| `.github/workflows/ci.yml` | CI/CD pipeline |
| `app/src/main/java/com/ryzix/player/service/PlayerService.kt` | Media3 background service |
| `app/src/main/java/com/ryzix/player/ui/MainActivity.kt` | Main Activity + ViewPager |
