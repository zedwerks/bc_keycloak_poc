#!/bin/bash
# This script sets up the hostnames for the PPID sector server environment.
sudo sh -c 'printf "\n# sector-json dev\n127.0.0.1 sector-a.local sector-b.local\n" >> /etc/hosts'