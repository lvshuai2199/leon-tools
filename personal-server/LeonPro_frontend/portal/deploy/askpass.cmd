@echo off
REM OpenSSH SSH_ASKPASS helper: prints DEPLOY_PASSWORD with no extra newline
powershell -NoProfile -Command "[Console]::Out.Write($env:DEPLOY_PASSWORD)"
