import fs from "fs";
import https from "https";
import express from "express";
import helmet from "helmet";

/**
 * CONFIG: set your allowed redirect URIs here.
 * These arrays must list the registered redirect URIs for each client.
 * It's fine if both list the same Postman callback; the SECTOR is the hostname of this server.
 */
const redirectsForA = [
  "https://oauth.pstmn.io/v1/callback"
  // add more if client-a has others
];

const redirectsForB = [
  "https://oauth.pstmn.io/v1/browser-callback"
  // add more if client-b has others
];

// Expected hostnames (as they appear in the Host header / certificate SAN)
const HOST_A = process.env.SECTOR_A_HOST || "sector-a.example.com";
const HOST_B = process.env.SECTOR_B_HOST || "sector-b.example.com";

// TLS certs (SAN must cover both HOST_A and HOST_B)
const CERT_FILE = process.env.TLS_CERT || "./sector-a.example.com+2.pem";
const KEY_FILE  = process.env.TLS_KEY  || "./sector-a.example.com+2-key.pem";

// ----------------------------------------------------------------------------

const app = express();
app.disable("x-powered-by");
app.use(helmet()); // safe defaults; doesn't affect JSON

// Serve a health check
app.get("/healthz", (_, res) => res.type("text/plain").send("ok"));

// Serve sector.json; choose content by Host header
app.get("/sector.json", (req, res) => {
  const host = (req.headers.host || "").split(":")[0].toLowerCase();

  if (host === HOST_A) {
    return res.type("application/json").send(JSON.stringify(redirectsForA));
  }
  if (host === HOST_B) {
    return res.type("application/json").send(JSON.stringify(redirectsForB));
  }

  // Unknown host — respond 404 to avoid leaking config
  res.status(404).type("application/json").send(JSON.stringify({ error: "unknown host" }));
});

// Strictly cacheable per spec (optional)
app.use((_, res, next) => {
  res.setHeader("Cache-Control", "public, max-age=300"); // 5 minutes
  next();
});

// ----------------------------------------------------------------------------

const tlsOptions = {
  key: fs.readFileSync(KEY_FILE),
  cert: fs.readFileSync(CERT_FILE),
  // strong defaults; adjust if needed
  honorCipherOrder: true,
  minVersion: "TLSv1.2"
};

const PORT = Number(process.env.PORT || 8443);
https.createServer(tlsOptions, app).listen(PORT, () => {
  console.log(`Sector JSON server on :${PORT}`);
  console.log(`→ https://${HOST_A}:${PORT}/sector.json`);
  console.log(`→ https://${HOST_B}:${PORT}/sector.json`);
});