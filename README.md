# Inventory Management Platform

Multi-tenant SaaS for inventory management, targeting four sub-systems (Retail/EC, Manufacturing, 3PL, Wholesale) on a shared backbone.

See [CLAUDE.md](./CLAUDE.md) for architectural conventions and non-obvious decisions.
See [docs/adr/](./docs/adr/) for architecture decisions.

## Layout

```
.
├── pom.xml                       Aggregator parent
├── docs/adr/                     Architecture Decision Records
├── inventory-commons/            Shared libraries (9 modules) — depended on by every service
└── services/
    └── inventory-core/           First service: write authority for inventory state (vertical spike)
```

## Build

```
mvn clean verify
```

## Run a single test

```
mvn -pl <module> -Dtest=<TestClass>#<method> test
```

## Format

```
mvn spotless:apply
```
