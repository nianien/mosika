# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21 Maven reactor with two modules:

- `mousika-core/` is the primary implementation module. It contains the rule engine, evaluator, UDF support, and the ANTLR grammar at `src/main/antlr4/Rule.g4`.
- `mousika-ui/` does not implement a user interface. It contains adapters and node models that convert core rule structures into data structures that frontends can render easily. Reference diagrams live in `src/main/resources/img/`.
- Production code follows the standard `src/main/java` layout; tests and fixtures are under `src/test/java` and `src/test/resources`.
- The root `pom.xml` owns shared dependency versions and build plugins. Do not edit generated ANTLR sources under `target/generated-sources/`.

## Build, Test, and Development Commands

Run commands from the repository root with JDK 21 and Maven installed:

- `mvn clean test` — rebuild both modules and run all JUnit tests.
- `mvn -pl mousika-core test` — test only the rule-engine module.
- `mvn -pl mousika-ui -am test` — test the frontend-data conversion module and build its core dependency.
- `mvn clean package` — compile, test, and create module JARs plus configured source/Javadoc artifacts.

ANTLR Java sources are generated automatically during the Maven build.

## Coding Style & Naming Conventions

Use four-space indentation and the existing Java brace style. Keep packages under `com.skyfalling.mousika`. Name classes in PascalCase, methods and fields in camelCase, and constants in `UPPER_SNAKE_CASE`. Prefer focused classes matching the current roles (`*Node`, `*Resolver`, `*Adapter`, `*Udf`). Lombok is already used; follow nearby code before introducing additional boilerplate or annotations. No formatter or static-analysis tool is configured, so preserve surrounding import ordering and formatting.

## Testing Guidelines

Tests use JUnit Jupiter 5 and Maven Surefire. Name test classes `*Test` and place them in the package corresponding to the production code. Add focused regression tests for parser, evaluation, concurrency, or tree-conversion changes. Store reusable JSON fixtures in `src/test/resources`. There is no configured coverage threshold; prioritize assertions on observable behavior and run `mvn clean test` before submitting.

## Commit & Pull Request Guidelines

Recent history uses short, descriptive subjects, often in Chinese, without Conventional Commit prefixes (for example, `增加UDF容器`). Keep each commit limited to one concern and use an imperative or outcome-focused subject. Pull requests should explain the behavior change, identify affected modules, list verification commands, and link related issues. For tree-conversion changes, include representative input and serialized or printed output.
