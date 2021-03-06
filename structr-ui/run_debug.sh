#!/bin/bash

# determine latest Structr version
LATEST=`ls target/structr-ui-*.jar | grep -v 'sources.jar' | grep -v 'javadoc.jar' | sort | tail -1`

# copy modules
./copy-modules.sh

BASE_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
LOGS_DIR=$BASE_DIR/logs

if [ ! -d $LOGS_DIR ]; then
        mkdir $LOGS_DIR
fi

# start Structr
java -Djava.awt.headless=true -Djava.system.class.loader=org.structr.StructrClassLoader -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false -Duser.timezone=Europe/Berlin -Duser.country=US -Duser.language=en -Djava.net.useSystemProxies=true -Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n -Dfile.encoding=utf-8 -Xms128m -Xms4096m -Xmx4096m -XX:+UseConcMarkSweepGC -XX:+UseNUMA -cp target/lib/*:$LATEST org.structr.Server | tee -a logs/debug.log
