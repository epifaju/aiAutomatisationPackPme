import { formatMoney, statusTone } from "@/lib/format";

describe("formatMoney", () => {
  it("formats euro amounts in French", () => {
    expect(formatMoney(120.5, "EUR")).toContain("120");
    expect(formatMoney("240.50", "EUR")).toMatch(/240/);
  });
});

describe("statusTone", () => {
  it("maps operational statuses", () => {
    expect(statusTone("OVERDUE")).toBe("bad");
    expect(statusTone("PENDING_APPROVAL")).toBe("warn");
    expect(statusTone("EXTRACTED")).toBe("ok");
  });
});
