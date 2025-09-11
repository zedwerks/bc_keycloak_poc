#!/bin/bash

docker compose up --build -d
# test both hosts against the single container:
curl -sk https://sector-a.local:8443/sector.json
curl -sk https://sector-b.local:8443/sector.json