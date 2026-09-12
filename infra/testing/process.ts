export type Command = { argv: string[]; env: Record<string, string>; cwd: string; timeoutMs?: number };
export type Executor = (command: Command) => Promise<void>;

export class HarnessFailure extends Error {
  constructor(readonly code: "PROCESS_FAILED" | "PROCESS_TIMEOUT" | "CLEANUP_FAILED") {
    super(code);
  }
}

/** Child output is deliberately not persisted: it can contain keys or provider payloads. */
export const execute: Executor = async ({ argv, env, cwd, timeoutMs = 600_000 }) => {
  let child: ReturnType<typeof Bun.spawn>;
  try { child = Bun.spawn(argv, { env, cwd, stdin: "ignore", stdout: "ignore", stderr: "ignore" }); }
  catch { throw new HarnessFailure("PROCESS_FAILED"); }
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; child.kill(9); }, timeoutMs);
  try {
    const code = await child.exited;
    if (timedOut) throw new HarnessFailure("PROCESS_TIMEOUT");
    if (code !== 0) throw new HarnessFailure("PROCESS_FAILED");
  } finally { clearTimeout(timer); }
};

/** Cleanup is attempted even if startup fails; cleanup failure never becomes success. */
export async function withCleanup(work: () => Promise<void>, cleanup: () => Promise<void>): Promise<void> {
  let failed: unknown;
  let didFail = false;
  try { await work(); } catch (error) { failed = error; didFail = true; }
  try { await cleanup(); } catch { throw new HarnessFailure("CLEANUP_FAILED"); }
  if (didFail) throw failed;
}
