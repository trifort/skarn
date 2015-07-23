bashScriptExtraDefines +=
  """
    |if [ -n $JAVA_OPTS ]; then
    |    JAVA_OPTS="-DCONFIG_PATH=$CONFIG_PATH -DSTATSD_HOST=$STATSD_HOST -javaagent:$lib_dir/org.aspectj.aspectjweaver-1.8.5.jar $JAVA_OPTS"
    |else
    |    JAVA_OPTS="-Xms256M -Xmx256M -Xss1M -XX:+UseParallelGC -DCONFIG_PATH=$CONFIG_PATH -DSTATSD_HOST=$STATSD_HOST -javaagent:$lib_dir/org.aspectj.aspectjweaver-1.8.5.jar"
    |fi
    |
    |""".stripMargin

AspectjKeys.aspectjVersion in Aspectj := "1.8.5"