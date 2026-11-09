@echo off
title fIPTV Web
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fiptv-web-pc.ps1" %*
