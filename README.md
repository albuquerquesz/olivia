<h1 align="center">Olivia</h1>

<p align="center">
  Sua companhia para gravar com Ray-Ban Meta: orientação por áudio sobre enquadramento, luz e horizonte, com as mãos livres.
</p>

<p align="center">
  <img src="apps/android/app/src/main/res/drawable-nodpi/olivia_room_3.png" alt="Olivia usando óculos Ray-Ban Meta na tela de boas-vindas" width="420" />
</p>

This project was created with [kubojs](https://github.com/albuquerquesz/kubo), a modern TypeScript stack that combines Elysia, and more.

## Features

- **TypeScript** - For type safety and improved developer experience
- **Elysia** - Type-safe, high-performance framework
- **Bun** - Runtime environment
- **Drizzle** - TypeScript-first ORM
- **PostgreSQL** - Database engine
- **Authentication** - Better-Auth
- **Turborepo** - Optimized monorepo build system

## Companion app Android — validação local

O flavor `mock` é um ambiente interno de validação: roda sem óculos, sem o SDK do
Device Access Toolkit e sem nenhum segredo. Ele não é um modo exposto na experiência
do produto. Em uma máquina limpa com JDK 17+ e o Android SDK (plataforma API 36), um
comando basta:

```bash
cd apps/android && ./gradlew assembleMockDebug
```

O APK sai em `apps/android/app/build/outputs/apk/mock/debug/`. A CI roda esse mesmo
comando e publica o APK como artefato interno do workflow `APK mock (debug)`, que dispara
manualmente (aba Actions → Run workflow) ou em push na branch `prod`.

Detalhes de flavors, versões e configuração local estão em
[`apps/android/README.md`](apps/android/README.md).

## Getting Started

First, install the dependencies:

```bash
bun install
```

## Database Setup

This project uses PostgreSQL with Drizzle ORM.

1. Start the local PostgreSQL instance with Docker Compose:

```bash
bun run db:up
```

2. Create the single environment file at the repository root:

```bash
cp .env.example .env
```

The root `.env` is shared by the entire Bun monorepo. Update it with the PostgreSQL connection string and local secrets when needed. Environment variables are loaded and validated by `@olivia/env`.

3. Apply the schema to your database:

```bash
bun run db:push
```

Stop PostgreSQL with `bun run db:down`. The Docker volume is preserved between runs.

Then, run the development server:

```bash
bun run dev
```

The API is running at [http://localhost:3000](http://localhost:3000).

## Project Structure

```
olivia/
├── apps/
│   ├── android/     # Companion app Kotlin/Compose (flavors mock e glasses)
│   └── server/      # Backend API (Elysia)
├── packages/
│   ├── auth/        # Authentication configuration & logic
│   ├── db/          # Database schema & queries
│   └── env/         # Shared environment loading & validation
└── docs/
    └── rules/       # Fonte de verdade: arquitetura, backlog e regras do DAT
```

## Available Scripts

- `bun run dev`: Start all applications in development mode
- `bun run build`: Build all applications
- `bun run dev:server`: Start only the server
- `bun run check-types`: Check TypeScript types across all apps
- `bun run db:up`: Start PostgreSQL with Docker Compose
- `bun run db:down`: Stop PostgreSQL without removing its volume
- `bun run db:logs`: Follow PostgreSQL logs
- `bun run db:push`: Push schema changes to database
- `bun run db:generate`: Generate database client/types
- `bun run db:migrate`: Run database migrations
- `bun run db:studio`: Open database studio UI
