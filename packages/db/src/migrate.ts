import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { env } from "@olivia/env/server";
import { drizzle } from "drizzle-orm/node-postgres";
import { migrate } from "drizzle-orm/node-postgres/migrator";

const migrationsFolder = resolve(fileURLToPath(new URL(".", import.meta.url)), "migrations");

await migrate(drizzle(env.DATABASE_URL), { migrationsFolder });
console.log("migrations applied");
