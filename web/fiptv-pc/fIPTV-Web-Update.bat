@echo off
title fIPTV Web - Update
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fiptv-web-pc.ps1" update
