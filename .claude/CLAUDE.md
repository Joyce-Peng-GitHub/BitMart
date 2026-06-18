## Mandatory Workflow: impl-test-review Loop

For every request that involves adding or modifying anything in the project (code, docs, config, etc.), you MUST execute the following loop via **SUB-AGENTS** before reporting back. Do NOT skip or short-circuit any phase.

---

### Phase 1 — Implement

Write or modify the required code and/or documentation.

### Phase 2 — Test (skip if the change is documentation-only)

- Write tests covering the new/changed behavior if none exist or the existing ones cannot cover it fully or correctly.
- Run the full relevant test suite.
- All tests must pass before proceeding. If any fail, return to Phase 1.

### Phase 3 — Review

Evaluate the current implementation against all of the following dimensions:

| Dimension       | Questions to answer                                          |
| --------------- | ------------------------------------------------------------ |
| Requirements    | Does the implementation fully satisfy the original request?  |
| Correctness     | Are there edge cases, off-by-one errors, or unhandled exceptions? |
| Performance     | Are there unnecessary allocations, N+1 queries, or blocking calls? |
| Security        | Are inputs validated? Any injection vectors, leaked secrets, or broken auth? |
| Maintainability | Is the code readable, consistent with project conventions, and not over-engineered? |
| Documentation   | Are public APIs, non-obvious logic, and behavioral changes documented? |

If **any** issue is found → return to Phase 1 with a clear description of what must be fixed. Repeat until all dimensions pass.

---

### Exit Condition

Only report back to the user after the loop exits cleanly (tests passed, no issues found). The report must include:

1. A summary of what was implemented/changed.
2. Test results (number of tests run, all passed).
3. A brief review summary confirming each dimension was checked.

---

## Workflow constraint

Unless explicitly asked, do NOT create worktrees. Edit the working copy directly on the main branch, and do not auto-commit or push. Let the user decide when and what to commit.