import { Hono } from "hono";
import { bodyLimit } from "hono/body-limit";
import { authenticationMiddleware } from "./auth";
import { ApiError, failure } from "./http";
import { enforceRateLimit, requestContextMiddleware, restrictedCorsMiddleware } from "./security";
import type { AppEnvironment } from "./types";
import { registerAccountRoutes } from "./routes/account";
import { registerBillingRoutes } from "./routes/billing";
import { registerBusinessRoutes } from "./routes/business";
import { registerHealthRoutes } from "./routes/health";
import { registerTaskRoutes } from "./routes/tasks";

export const app = new Hono<AppEnvironment>();

app.use("*", requestContextMiddleware);
app.use("/v1/*", bodyLimit({
  maxSize: 1 * 1024 * 1024,
  onError: (c) => failure(c, 413, "REQUEST_BODY_TOO_LARGE", "Request bodies are limited to 1 MiB."),
}));
app.use("*", restrictedCorsMiddleware);

app.use("/v1/*", async (c, next) => {
  if (c.req.path === "/v1/health") {
    await next();
    return;
  }
  await authenticationMiddleware(c, async () => {
    const limit = await enforceRateLimit(c.env, c.get("principal").id, "api");
    c.header("X-RateLimit-Limit", String(limit.limit));
    c.header("X-RateLimit-Remaining", String(limit.remaining));
    c.header("X-RateLimit-Reset", String(limit.resetAtSeconds));
    await next();
  });
});

registerHealthRoutes(app);
registerTaskRoutes(app);
registerBusinessRoutes(app);
registerBillingRoutes(app);
registerAccountRoutes(app);

app.notFound((c) => failure(c, 404, "NOT_FOUND", "Route not found."));

app.onError((error, c) => {
  if (error instanceof ApiError) {
    return failure(c, error.status, error.code, error.message, error.details);
  }
  return failure(c, 500, "INTERNAL_ERROR", "The request could not be completed.");
});

export default app;
