#!/bin/bash

# Go to the project root (where this script is located)
cd "$(dirname "$0")"

# Step 1: Build the plugin (calls Gradle wrapper)
./gradlew build || { echo "❌ Build failed. Server not started."; exit 1; }

# Step 2: Ensure plugins folder exists
mkdir -p server/plugins

# Step 3: Copy latest JAR to server plugins folder
cp build/libs/*.jar server/plugins/

# Step 4: Launch Purpur server
cd server
java -jar purpur-1.21.4-2416.jar nogui
