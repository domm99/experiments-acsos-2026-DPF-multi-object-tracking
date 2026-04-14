#!/usr/bin/env sh
DESTINATION="$HOME/Downloads/moving-sensors-neighbors-based-$(date --utc "+%F-%H.%M.%S")"
git clone https://github.com/domm99/experiments-acsos-2026-DPF-multi-object-tracking "$DESTINATION"
cd "$DESTINATION"
./gradlew runMovingSensorsNBG