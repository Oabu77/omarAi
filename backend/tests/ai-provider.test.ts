import { describe, expect, it } from "vitest";
import { CloudflareWorkersAiProvider } from "../src/ai/cloudflare";

describe("Cloudflare Workers AI adapter", () => {
  const request = {
    text: "Prepare a quote",
    locale: "en",
    conversationId: "conversation-1",
  };

  it("returns AI_DISCONNECTED without the binding", async () => {
    await expect(new CloudflareWorkersAiProvider({}).plan(request)).rejects.toMatchObject({
      code: "AI_DISCONNECTED",
    });
  });

  it("uses the configurable text model and validates provider output", async () => {
    let invokedModel = "";
    const provider = new CloudflareWorkersAiProvider({
      MODEL_TEXT: "@cf/example/model",
      AI: {
        async run(model) {
          invokedModel = model;
          return {
            response: JSON.stringify({
              title: "Prepare quote",
              intent: "estimate",
              reply: "I prepared a plan. Nothing was sent.",
              steps: [{
                agent: "Estimating Agent",
                action: "Calculate a draft estimate",
                requiresApproval: false,
                externalAction: false,
              }],
            }),
          };
        },
      },
    });
    const result = await provider.plan(request);
    expect(invokedModel).toBe("@cf/example/model");
    expect(result.evidence).toEqual({ state: "PROVIDER_VERIFIED", operation: "model_inference" });
    expect(result.plan.steps[0]?.externalAction).toBe(false);
  });
});
