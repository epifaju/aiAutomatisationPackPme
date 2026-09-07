/**
 * Generates n8n 1.107 workflow JSON (no credentials).
 * Run: node n8n/build-workflows.mjs
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const outDir = join(dirname(fileURLToPath(import.meta.url)), "workflows");
mkdirSync(outDir, { recursive: true });

const ERROR_WF = "wf091errorhandl";
const DEMO_COMPANY = "aaaaaaaa-0000-4000-8000-000000000001";

function settings(errorWorkflow = ERROR_WF) {
  return {
    executionOrder: "v1",
    timezone: "Europe/Paris",
    callerPolicy: "workflowsFromSameOwner",
    ...(errorWorkflow ? { errorWorkflow } : {}),
  };
}

function webhookNode(id, path) {
  return {
    parameters: {
      httpMethod: "POST",
      path,
      responseMode: "lastNode",
      options: {},
    },
    id,
    name: "Webhook",
    type: "n8n-nodes-base.webhook",
    typeVersion: 2.1,
    position: [240, 300],
    webhookId: id,
  };
}

function scheduleNode(id, hour, minute) {
  return {
    parameters: {
      rule: {
        interval: [{ field: "days", daysInterval: 1, triggerAtHour: hour, triggerAtMinute: minute }],
      },
    },
    id,
    name: "Schedule",
    type: "n8n-nodes-base.scheduleTrigger",
    typeVersion: 1.2,
    position: [240, 300],
  };
}

function codeNode(id, jsCode) {
  return {
    parameters: { jsCode },
    id,
    name: "Prepare payload",
    type: "n8n-nodes-base.code",
    typeVersion: 2,
    position: [460, 300],
  };
}

function httpNode(id, backendPath, timeout = 120000) {
  return {
    parameters: {
      method: "POST",
      url: `={{ $env.BACKEND_BASE_URL }}/${backendPath.replace(/^\//, "")}`,
      sendHeaders: true,
      headerParameters: {
        parameters: [
          { name: "X-Webhook-Secret", value: "={{ $env.WEBHOOK_SECRET }}" },
          { name: "Content-Type", value: "application/json" },
        ],
      },
      sendBody: true,
      specifyBody: "json",
      jsonBody: "={{ JSON.stringify($json) }}",
      options: { timeout },
    },
    id,
    name: "Backend",
    type: "n8n-nodes-base.httpRequest",
    typeVersion: 4.2,
    position: [680, 300],
    retryOnFail: true,
    maxTries: 2,
    waitBetweenTries: 2000,
  };
}

function connect(from, to) {
  return { [from]: { main: [[{ node: to, type: "main", index: 0 }]] } };
}

function assertSecretAndBody(extraAssignments) {
  return `const item = $input.first().json;
const headers = item.headers || {};
const provided = headers['x-webhook-secret'] || headers['X-Webhook-Secret'];
const expected = (typeof $env !== 'undefined' && $env.WEBHOOK_SECRET) || process.env.WEBHOOK_SECRET;
if (!expected || provided !== expected) {
  throw new Error('Unauthorized webhook');
}
const body = item.body && typeof item.body === 'object' ? item.body : item;
const companyId = body.companyId || (typeof $env !== 'undefined' && $env.DEMO_COMPANY_ID) || '${DEMO_COMPANY}';
const payload = { ...body, companyId${extraAssignments} };
return [{ json: payload }];`;
}

function scheduleBody(extraJs) {
  return `const companyId = (typeof $env !== 'undefined' && $env.DEMO_COMPANY_ID) || '${DEMO_COMPANY}';
return [{ json: { companyId${extraJs} } }];`;
}

function webhookWorkflow({ id, name, description, version, path, backendPath, extraAssignments, timeout }) {
  return {
    id,
    name,
    description: `${description} · v${version}`,
    nodes: [
      webhookNode(`${id}-hook`, path),
      codeNode(`${id}-code`, assertSecretAndBody(extraAssignments)),
      httpNode(`${id}-http`, backendPath, timeout),
    ],
    connections: { ...connect("Webhook", "Prepare payload"), ...connect("Prepare payload", "Backend") },
    settings: settings(),
    staticData: null,
    meta: { templateCredsSetupCompleted: true },
    pinData: {},
    versionId: `${id}-v${version}`,
  };
}

function scheduleWorkflow({ id, name, description, version, hour, minute, backendPath, extraJs, timeout }) {
  return {
    id,
    name,
    description: `${description} · v${version}`,
    nodes: [
      scheduleNode(`${id}-sched`, hour, minute),
      codeNode(`${id}-code`, scheduleBody(extraJs)),
      httpNode(`${id}-http`, backendPath, timeout),
    ],
    connections: { ...connect("Schedule", "Prepare payload"), ...connect("Prepare payload", "Backend") },
    settings: settings(),
    staticData: null,
    meta: { templateCredsSetupCompleted: true },
    pinData: {},
    versionId: `${id}-v${version}`,
  };
}

const workflows = [
  webhookWorkflow({
    id: "wf001emailingst",
    name: "[AIPACK][EMAIL] Ingestion",
    description: "Ingests an inbound email without calling the LLM (analyze=false).",
    version: "1.0.0",
    path: "aipack/email/incoming",
    backendPath: "webhook/email/incoming",
    extraAssignments: ", analyze: false",
    timeout: 30000,
  }),
  webhookWorkflow({
    id: "wf002emailanlys",
    name: "[AIPACK][EMAIL] Analyse Email",
    description: "Ingests or re-analyzes an email with Ollama classification (analyze=true).",
    version: "1.0.0",
    path: "aipack/email/analyze",
    backendPath: "webhook/email/incoming",
    extraAssignments: ", analyze: true",
    timeout: 180000,
  }),
  webhookWorkflow({
    id: "wf003emailreply",
    name: "[AIPACK][EMAIL] Reply generation",
    description:
      "Produces a suggested reply via backend analyze. Never auto-sends (AI_GENERATED_EMAIL_AUTO_SEND=false).",
    version: "1.0.0",
    path: "aipack/email/reply",
    backendPath: "webhook/email/incoming",
    extraAssignments: ", analyze: true",
    timeout: 180000,
  }),
  webhookWorkflow({
    id: "wf010leadcaptur",
    name: "[AIPACK][LEAD] Capture",
    description: "Captures a lead from a form/webhook without qualification (qualify=false).",
    version: "1.0.0",
    path: "aipack/leads/create",
    backendPath: "webhook/leads/create",
    extraAssignments: ", qualify: false",
    timeout: 30000,
  }),
  webhookWorkflow({
    id: "wf011leadqualif",
    name: "[AIPACK][LEAD] Qualification",
    description: "Creates a lead and runs Ollama qualification (qualify=true).",
    version: "1.0.0",
    path: "aipack/leads/qualify",
    backendPath: "webhook/leads/create",
    extraAssignments: ", qualify: true",
    timeout: 180000,
  }),
  webhookWorkflow({
    id: "wf020docingesti",
    name: "[AIPACK][DOC] Ingestion",
    description: "Stores a document in MinIO without LLM extraction (process=false).",
    version: "1.0.0",
    path: "aipack/documents/ingest",
    backendPath: "webhook/documents/process",
    extraAssignments: ", process: false",
    timeout: 60000,
  }),
  webhookWorkflow({
    id: "wf021docextract",
    name: "[AIPACK][DOC] Extraction",
    description: "Stores then extracts document fields with Tika + Ollama (process=true).",
    version: "1.0.0",
    path: "aipack/documents/process",
    backendPath: "webhook/documents/process",
    extraAssignments: ", process: true",
    timeout: 180000,
  }),
  scheduleWorkflow({
    id: "wf030invoverdue",
    name: "[AIPACK][INVOICE] Overdue detection",
    description: "Daily 08:00 Europe/Paris overdue detection (idempotent on invoice + reminder level).",
    version: "1.0.0",
    hour: 8,
    minute: 0,
    backendPath: "webhook/invoices/reminder",
    extraJs: ", send: false",
    timeout: 60000,
  }),
  webhookWorkflow({
    id: "wf031invremindr",
    name: "[AIPACK][INVOICE] Reminder",
    description: "On-demand overdue detection / reminder generation. J+30 stays manual; auto-send off by default.",
    version: "1.0.0",
    path: "aipack/invoices/reminder",
    backendPath: "webhook/invoices/reminder",
    extraAssignments: ", send: false",
    timeout: 60000,
  }),
  scheduleWorkflow({
    id: "wf040dailyrepor",
    name: "[AIPACK][REPORT] Daily Report",
    description: "Daily 07:30 Europe/Paris report generation. Email send stays opt-in.",
    version: "1.0.0",
    hour: 7,
    minute: 30,
    backendPath: "webhook/reports/daily",
    extraJs: ", send: false",
    timeout: 180000,
  }),
];

const auditLogger = {
  id: "wf090auditloggr",
  name: "[AIPACK][AUDIT] Logger",
  description: "Normalizes n8n execution metadata. Secrets are stripped. Backend audit remains source of truth. · v1.0.0",
  nodes: [
    webhookNode("wf090auditloggr-hook", "aipack/audit"),
    {
      parameters: {
        jsCode: `const item = $input.first().json;
const headers = item.headers || {};
const provided = headers['x-webhook-secret'] || headers['X-Webhook-Secret'];
const expected = (typeof $env !== 'undefined' && $env.WEBHOOK_SECRET) || process.env.WEBHOOK_SECRET;
if (!expected || provided !== expected) {
  throw new Error('Unauthorized webhook');
}
const body = item.body && typeof item.body === 'object' ? item.body : item;
const redact = (value) => {
  if (value && typeof value === 'object') {
    const out = Array.isArray(value) ? [] : {};
    for (const [key, nested] of Object.entries(value)) {
      const lower = key.toLowerCase();
      out[key] = ['password', 'secret', 'token', 'authorization'].some((part) => lower.includes(part))
        ? '[redacted]'
        : redact(nested);
    }
    return out;
  }
  return value;
};
return [{ json: {
  workflow: body.workflow || 'n8n',
  execution: body.execution || $execution.id,
  status: body.status || 'SUCCESS',
  error_type: body.error_type || null,
  error_message: body.error_message || null,
  timestamp: new Date().toISOString(),
  metadata: redact(body.metadata || {}),
} }];`,
      },
      id: "wf090auditloggr-code",
      name: "Sanitize audit",
      type: "n8n-nodes-base.code",
      typeVersion: 2,
      position: [460, 300],
    },
  ],
  connections: connect("Webhook", "Sanitize audit"),
  settings: settings(),
  staticData: null,
  meta: { templateCredsSetupCompleted: true },
  pinData: {},
  versionId: "wf090auditloggr-v1.0.0",
};

// Keep inactive: Error Trigger is not activatable as a normal workflow start node.
// Referenced by other workflows via settings.errorWorkflow.
const errorHandler = {
  id: ERROR_WF,
  name: "[AIPACK][ERROR] Handler",
  description:
    "Error workflow (keep inactive). Sanitizes failed execution payloads and posts ERROR to backend audit. Invoked via settings.errorWorkflow. · v1.1.0",
  nodes: [
    {
      parameters: {},
      id: `${ERROR_WF}-trigger`,
      name: "Error Trigger",
      type: "n8n-nodes-base.errorTrigger",
      typeVersion: 1,
      position: [240, 300],
    },
    {
      parameters: {
        jsCode: `const item = $input.first().json;
const companyId = (typeof $env !== 'undefined' && $env.DEMO_COMPANY_ID) || '${DEMO_COMPANY}';
const redact = (value) => {
  if (value && typeof value === 'object') {
    const out = Array.isArray(value) ? [] : {};
    for (const [key, nested] of Object.entries(value)) {
      const lower = String(key).toLowerCase();
      out[key] = ['password', 'secret', 'token', 'authorization'].some((part) => lower.includes(part))
        ? '[redacted]'
        : redact(nested);
    }
    return out;
  }
  return value;
};
const workflowName = item.workflow?.name || item.workflowName || 'unknown';
const executionId = item.execution?.id || item.executionId || null;
return [{ json: {
  companyId,
  workflow: String(workflowName).slice(0, 128),
  execution: executionId == null ? null : String(executionId).slice(0, 64),
  errorType: item.execution?.error?.name || 'WORKFLOW_ERROR',
  errorMessage: String(item.execution?.error?.message || 'Workflow execution failed').slice(0, 2000),
  metadata: redact({
    lastNode: item.execution?.lastNodeExecuted || null,
    n8nWorkflowId: item.workflow?.id || null,
  }),
} }];`,
      },
      id: `${ERROR_WF}-code`,
      name: "Sanitize error",
      type: "n8n-nodes-base.code",
      typeVersion: 2,
      position: [460, 300],
    },
    {
      ...httpNode(`${ERROR_WF}-http`, "webhook/audit/n8n-error", 30000),
      name: "Backend audit",
    },
  ],
  connections: {
    ...connect("Error Trigger", "Sanitize error"),
    ...connect("Sanitize error", "Backend audit"),
  },
  settings: settings(null),
  staticData: null,
  meta: { templateCredsSetupCompleted: true },
  pinData: {},
  versionId: `${ERROR_WF}-v1.1.0`,
};

for (const workflow of [...workflows, auditLogger, errorHandler]) {
  workflow.active = false;
  const file = join(outDir, `${workflow.id}.json`);
  writeFileSync(file, `${JSON.stringify(workflow, null, 2)}\n`);
  console.info(`wrote ${file}`);
}
