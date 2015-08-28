bashScriptExtraDefines +=
  """
    |if [ -n "$JAVA_OPTS" ]; then
    |    JAVA_OPTS="-DCONFIG_PATH=$CONFIG_PATH -DKAMON_HOST=$KAMON_HOST -DKAMON_PORT=$KAMON_PORT -javaagent:$lib_dir/org.aspectj.aspectjweaver-1.8.5.jar $JAVA_OPTS"
    |else
    |    JAVA_OPTS="-Xms256M -Xmx256M -Xss1M -XX:+UseParallelGC -DCONFIG_PATH=$CONFIG_PATH -DKAMON_HOST=$KAMON_HOST -DKAMON_PORT=$KAMON_PORT -javaagent:$lib_dir/org.aspectj.aspectjweaver-1.8.5.jar"
    |fi
    |
    |if [ -n "$MAX_QUEUE_SIZE" ]; then
    |    JAVA_OPTS=" -DMAX_QUEUE_SIZE=$MAX_QUEUE_SIZE $JAVA_OPTS"
    |fi
    |""".stripMargin

AspectjKeys.aspectjVersion in Aspectj := "1.8.5"