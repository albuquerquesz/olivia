FROM oven/bun:1.3.10-slim
WORKDIR /workspace
COPY packages/env/src/infra.ts packages/env/src/infra.ts
COPY infra/testing infra/testing
USER bun
ENTRYPOINT ["bun", "--no-env-file"]
CMD ["test", "infra/testing/tests"]
