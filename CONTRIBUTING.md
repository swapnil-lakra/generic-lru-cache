# Contributing to Generic LRU Cache

Thanks for considering contributing! This project follows a Git Flow-inspired
branching strategy.

## Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production-ready code. Merges only via PR from `develop`. |
| `develop` | Integration branch. Feature branches merge here first. |
| `feature/*` | New features. Branch off from `develop`. |
| `fix/*` | Non-urgent bug fixes. Branch off from `develop`. |
| `refactor/*` | Code restructuring (no behavior change). |
| `test/*` | Test-only additions/updates. |
| `docs/*` | Documentation updates. |
| `perf/*` | Performance improvements. |
| `chore/*` | Build, dependencies, CI, tooling. |
| `hotfix/*` | Urgent production fixes. Branch from `main`. |

## Naming Convention

`<type>/<short-kebab-case-description>`

Examples:
- `feature/lru-eviction-policy`
- `fix/ttl-overflow`
- `docs/readme-quickstart`

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

`<type>(<scope>): <short description>`


Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`, `ci`, `build`.

## Pull Request Workflow

1. Fork / create a branch off `develop`
2. Make focused commits
3. Push branch and open a PR against `develop`
4. Ensure CI passes and PR template is filled
5. Squash-merge once approved

## Local Development

```bash
mvn clean compile     # compile
mvn test              # run tests
mvn clean verify      # full build + coverage