#!/bin/sh
pkill -f  "java -jar kameo-commander-0.0.1-SNAPSHOT.jar"
cd /home/vinga/kameocommander
nohup java -jar  kameo-commander-0.0.1-SNAPSHOT.jar > log.log 2>&1 &

# ufw allow 8111 comment 'allow kameo commander'
