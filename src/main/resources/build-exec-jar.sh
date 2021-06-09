#!/bin/sh
TARGET="target/filesorter.run"
echo "Creating runnable Linux jar in ${TARGET}..."
cat src/main/resources/java-start.sh target/*.jar > ${TARGET} && chmod +x ${TARGET}
echo "Done"