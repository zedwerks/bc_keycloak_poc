import express from "express";
import crypto from "crypto";

const app = express();
app.use(express.json());

/**
 * In-memory store
 * Replace with DB in production
 */
const store = new Map();

/**
 * CONFIG
 */
const EXPECTED_ISSUER = process.env.EXPECTED_ISSUER;
const REQUIRED_AUDIENCE = process.env.REQUIRED_AUDIENCE;
const PORT = process.env.PORT || 3000;

/**
 * Validate config early
 */
if (!EXPECTED_ISSUER || !REQUIRED_AUDIENCE) {
    console.error("❌ Missing required environment variables:");
    console.error("   EXPECTED_ISSUER");
    console.error("   REQUIRED_AUDIENCE");
    process.exit(1);
}
/**
 * Basic JWT decode (NO signature validation – reference only)
 */
function decodeJwt(token) {
    try {
        const parts = token.split(".");
        if (parts.length !== 3) return null;

        const payload = JSON.parse(
            Buffer.from(parts[1], "base64url").toString("utf-8")
        );

        return payload;
    } catch {
        return null;
    }
}

/**
 * Bearer auth middleware
 */
function authMiddleware(req, res, next) {
    const auth = req.headers.authorization;

    if (!auth || !auth.startsWith("Bearer ")) {
        return res.status(401).json({ error: "Missing bearer token" });
    }

    const token = auth.substring("Bearer ".length);
    const payload = decodeJwt(token);

    if (!payload) {
        return res.status(401).json({ error: "Invalid token format" });
    }

    // Basic checks
    if (payload.iss !== EXPECTED_ISSUER) {
        return res.status(401).json({ error: "Invalid issuer" });
    }

    /*
    if (payload.aud !== REQUIRED_AUDIENCE) {
        return res.status(401).json({ error: "Invalid audience" });
    } */

    const now = Math.floor(Date.now() / 1000);
    if (payload.exp && payload.exp < now) {
        return res.status(401).json({ error: "Token expired" });
    }

    // attach token for downstream use if needed
    req.token = payload;

    console.log(`Authenticated request from ${payload.sub}`);

    next();
}

/**
 * Generate UUID
 */
function generateId() {
    return crypto.randomUUID();
}

app.get("/", (req, res) => {
    res.json({ status: "ok" });
});

/**
 * POST /delegations
 * Stores delegation and returns ID
 * This would not be how the delegation service sets the delegation contract ID in a real implementation, 
 * but serves to demonstrate the flow
 */
app.post("/delegations", authMiddleware, (req, res) => {
    const delegation = req.body;

    // Basic validation
    if (!delegation || typeof delegation !== "object") {
        return res.status(400).json({ error: "Invalid JSON body" });
    }

    console.log("Received delegation contract:", delegation);

    const delegation_contract_id = generateId();

    delegation.id = delegation_contract_id; // assign ID to delegation for reference

    const stored = {
        ...delegation,
        id: delegation_contract_id
    };

    store.set(delegation_contract_id, stored);

    return res.status(201).json({
        delegation_contract_id
    });
});

/**
 * GET /delegations/:id
 * This would ONLY be allowed if the requester is Keycloak (validated via token claims) in a real implementation, 
 * but we keep it open here for demonstration purposes
 */
app.get("/delegations/:id", authMiddleware, (req, res) => {
    const { id } = req.params;

    console.log(`Fetching delegation contract with ID: ${id}`);

    const delegation = store.get(id);

    if (!delegation) {
        return res.status(404).json({ error: "Not found" });
    }

    return res.json(delegation);
});

/**
 * Health check
 */
app.get("/health", (req, res) => {
    res.json({ status: "ok" });
});

/**
 * Start server
 */
app.listen(PORT, () => {
    console.log(`🚀 Delegation server running on http://localhost:${PORT}`);
});