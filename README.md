# olivia

This project was created with [kubojs](https://github.com/albuquerquesz/kubo), a modern TypeScript stack that combines Elysia, and more.

## Features

- **TypeScript** - For type safety and improved developer experience
- **Elysia** - Type-safe, high-performance framework
- **Bun** - Runtime environment
- **Turborepo** - Optimized monorepo build system

## Getting Started

First, install the dependencies:

```bash
bun install
```

Then, run the development server:

```bash
bun run dev
```

The API is running at [http://localhost:3000](http://localhost:3000).

## Project Structure

```
olivia/
├── apps/
│   └── server/      # Backend API (Elysia)
├── packages/
```

## Available Scripts

- `bun run dev`: Start all applications in development mode
- `bun run build`: Build all applications
- `bun run dev:server`: Start only the server
- `bun run check-types`: Check TypeScript types across all apps
