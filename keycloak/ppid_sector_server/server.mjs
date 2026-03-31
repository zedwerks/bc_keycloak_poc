import fs from "fs";
import https from "https";
import express from "express";
import helmet from "helmet";

/**
 * CONFIG: set your allowed redirect URIs here.
 * These arrays must list the registered redirect URIs for each client.
 * It's fine if both list the same Postman callback; the SECTOR is the hostname of this server.
 */


// Expected hostnames (as they appear in the Host header / certificate SAN)
const HOST_SECTOR_SVC = process.env.HOST_SECTOR_SVC || "sector-svc.example.com";

// TLS certs (SAN must cover both HOST_SECTOR_SVC and HOST_B)
const CERT_FILE = process.env.TLS_CERT || "./cert.pem";
const KEY_FILE  = process.env.TLS_KEY  || "./sector-svc.example.com+2-key.pem";

// ----------------------------------------------------------------------------

const app = express();
app.disable("x-powered-by");
app.use(helmet()); // safe defaults; doesn't affect JSON

// Serve a health check
app.get("/healthz", (_, res) => res.type("text/plain").send("ok"));

var sector_a = [
  "client-a1",
  "client-a2"
];

var sector_b = [
  "client-b",
  "client-b-rs"
]

var sector_c = [
  "client-c",
  "client-c-rs"
]

app.get("/sector_a.json", (req, res) => {
  return res.type("application/json").send(JSON.stringify(sector_a));
});

app.get("/sector_b.json", (req, res) => {
  return res.type("application/json").send(JSON.stringify(sector_b));
});

app.get("/sector_c.json", (req, res) => {
  return res.type("application/json").send(JSON.stringify(sector_c));
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
  console.log(`→ https://${HOST_SECTOR_SVC}:${PORT}/sector_a.json`);
  console.log(`→ https://${HOST_SECTOR_SVC}:${PORT}/sector_b.json`);
  console.log(`→ https://${HOST_SECTOR_SVC}:${PORT}/sector_c.json`);
});