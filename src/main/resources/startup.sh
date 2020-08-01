#!/bin/sh
pkill -f  "java -jar kameo-commander-0.0.1-SNAPSHOT.jar"
cd /home/vinga/kameocommander
java -jar  kameo-commander-0.0.1-SNAPSHOT.jar

# ufw allow 8111 comment 'allow kameo commander'
