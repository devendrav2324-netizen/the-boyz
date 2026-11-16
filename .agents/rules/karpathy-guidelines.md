---
description: Karpathy behavioral guidelines to reduce common LLM coding mistakes (Think before coding, Simplicity first, Surgical changes, Goal-driven execution).
always_on: true
---

# Karpathy Coding Guidelines

These guidelines apply to all tasks in this project:

## 1. Think Before Coding
- State assumptions explicitly. If uncertain, ask rather than guess.
- Present multiple interpretations when ambiguity exists.
- Push back if a simpler or cleaner approach exists.
- Stop and ask when confused.

## 2. Simplicity First
- Minimum code to solve the problem. Nothing speculative.
- No unused abstractions, premature configurability, or bloated wrappers.
- If 200 lines could be 50, rewrite it simply.

## 3. Surgical Changes
- Touch only what is strictly necessary for the request.
- Do not refactor adjacent or unrelated code.
- Clean up only imports/variables created or orphaned by your own change.
- Match existing style and conventions.

## 4. Goal-Driven Execution
- State verifiable step-by-step plans: `[Step] -> verify: [check]`
- Verify every change with unit tests and builds.
