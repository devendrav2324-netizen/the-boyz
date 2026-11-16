# Project Guidelines & Operating Methodology (Karpathy Principles)

These behavioral guidelines govern all work on this project, derived from Andrej Karpathy's observations on LLM coding pitfalls.
**Strict Rule:** Follow these principles and methodology continuously in this project until explicitly prompted otherwise by the user.

---

## 1. Think Before Coding
**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing any request:
- **State assumptions explicitly:** If uncertain about UI layout, behavior, data schemas, or requirements, state the assumptions or ask rather than guessing.
- **Present multiple interpretations:** If a user request has multiple interpretations, present them clearly—do not silently pick one and run with it.
- **Push back when warranted:** If a simpler, more robust, or less bloated approach exists, say so and explain the tradeoff.
- **Stop when confused:** If something is ambiguous or conflicting, stop immediately, identify what is confusing, and ask for clarification.

---

## 2. Simplicity First
**Minimum code that solves the problem. Nothing speculative.**

- **No features beyond what was asked:** Build only what the user requested.
- **No abstractions for single-use code:** Do not introduce helper classes, generics, or design patterns for code used only once.
- **No speculative flexibility or configurability:** Avoid adding parameters or configuration toggles that were not requested.
- **No defensive error handling for impossible scenarios:** Write lean, direct logic without defensive bloat.
- **The Senior Engineer Test:** Would a senior engineer say this is overcomplicated? If yes, simplify. If 200 lines could be 50, rewrite it.

---

## 3. Surgical Changes
**Touch only what you must. Clean up only your own mess.**

When modifying existing code:
- **No drive-by refactoring:** Do not "improve" adjacent code, comments, or formatting orthogonal to the task.
- **Do not refactor unbroken code:** Keep existing working implementations intact.
- **Match existing style:** Follow the project's established conventions, naming, and patterns.
- **Unrelated dead code:** If noticed, mention it in text—never delete it unless asked.
- **Clean up your own orphans:** If your edit makes an import, variable, or helper unused, remove it. Do not touch pre-existing unused elements.
- **The Traceability Test:** Every single changed line must trace directly to the user's request.

---

## 4. Goal-Driven Execution
**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals with verification loops:
- For multi-step tasks, state a brief plan before executing:
  ```
  1. [Step] -> verify: [check]
  2. [Step] -> verify: [check]
  3. [Step] -> verify: [check]
  ```
- Write and run unit tests or compiler checks to verify every step before declaring completion.
- Loop and fix issues autonomously against clear success criteria.

---

## 5. Standing Project Release Rule (`RELEASE_RULE.md`)
Always respect the project's release and deployment requirements:
1. Version increment: Patch +1, Version Code +1.
2. Synchronize: `app/build.gradle.kts`, `version_name`, `apk/version_name`, and `AppUpdateManager.kt`.
3. Permanent APK Preservation: Never delete or overwrite previous APKs in `apk/`.
4. Verification: Run `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest` and `assembleDebug`.
5. Deploy: Commit, push to `origin/main`, and verify raw GitHub endpoints.
