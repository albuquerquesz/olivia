import { expect, test } from "bun:test";
import { execute, withCleanup } from "../process";
import { childEnvironment } from "../../../packages/env/src/infra";

test("cleanup runs after successful work", async () => {
  const order: string[] = [];
  await withCleanup(async () => { order.push("work"); }, async () => { order.push("cleanup"); });
  expect(order).toEqual(["work", "cleanup"]);
});
test("startup failure still cleans up and remains failure", async () => {
  let cleaned = false;
  await expect(withCleanup(async () => { throw new Error("STARTUP"); }, async () => { cleaned = true; })).rejects.toThrow("STARTUP");
  expect(cleaned).toBe(true);
});
test("cleanup failure fails the run", async () => {
  await expect(withCleanup(async () => {}, async () => { throw new Error("private details"); })).rejects.toThrow("CLEANUP_FAILED");
});
test("real child process errors and timeouts are sanitized", async () => {
  const common = { env: childEnvironment(process.env), cwd: process.cwd() };
  await execute({ ...common, argv: [process.execPath, "--no-env-file", "-e", "process.exit(0)"] });
  await expect(execute({ ...common, argv: [process.execPath, "--no-env-file", "-e", "console.error('private');process.exit(7)"] })).rejects.toThrow("PROCESS_FAILED");
  await expect(execute({ ...common, argv: [process.execPath, "--no-env-file", "-e", "setTimeout(()=>{},10000)"], timeoutMs: 100 })).rejects.toThrow("PROCESS_TIMEOUT");
});
