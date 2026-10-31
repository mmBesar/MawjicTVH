# CI hotfix: unresolved `onDispose` import

The failed CI run stopped while compiling shared Compose code because `App.kt` imported
`androidx.compose.runtime.onDispose` as if it were a top-level function. It is instead
a member of `DisposableEffectScope`, so it is called inside the `DisposableEffect {}`
receiver block without an import.

The fix removes only that invalid import. The `DisposableEffect(client)` cleanup remains
unchanged. `scripts/source-sanity.sh` now checks for the invalid import and prints a direct
explanation if it is reintroduced.

## Apply to the already-pushed GitHub repository

Extract `MawjicTVH-onDispose-fix.bundle` from the supplied ZIP, then run from your existing
local MawjicTVH repository (on branch `main`):

```bash
git switch main
git fetch /path/to/MawjicTVH-onDispose-fix.bundle main
git merge --ff-only FETCH_HEAD
git push origin main
```

Alternatively, clone the bundle into a fresh directory, set `origin` to
`git@github.com:mmBesar/MawjicTVH.git`, and push `main`. The hotfix commit is a direct child
of the previous `cc9d131` baseline, so updating an unchanged remote `main` is a fast-forward.

## Verification status

- Source-sanity check: passed.
- Shell syntax and `git diff --check`: passed.
- Gradle compilation could not be run in the authoring environment because
  `services.gradle.org` could not be resolved. Hosted GitHub Actions must confirm the fix
  and reveal any subsequent errors.
