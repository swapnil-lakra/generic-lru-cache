# GitHub Actions Workflows

## `build.yml`

Runs on every push to `main` and `develop`, and on every pull request
targeting either branch.

**What it does:**

1. Matrix build across JDK 11, 17, and 21 (Temurin distribution)
2. `mvn -B clean verify` — compiles, runs all tests, and enforces the
   JaCoCo coverage gate (LINE ≥ 85%, BRANCH ≥ 70%)
3. Uploads the JaCoCo HTML report as an artifact on the JDK 17 leg
4. Uploads surefire XML reports on failure

**Concurrency:** pushing to the same branch or PR cancels the previous
in-flight run. Saves CI minutes.

**Required status check:** `Build (JDK 11)`, `Build (JDK 17)`,
`Build (JDK 21)` — should be configured as required checks on `main`
and `develop` branch protection rules.