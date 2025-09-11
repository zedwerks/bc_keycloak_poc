#!/bin/bash
mkcert -install
mkcert -cert-file certs/cert.pem -key-file certs/key.pem \
  sector-a.example.com sector-b.example.com