#!/usr/bin/env bash
# OpenSSH SSH_ASKPASS helper
printf '%s' "${DEPLOY_PASSWORD-}"
