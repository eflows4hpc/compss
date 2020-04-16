source "${COMPSS_HOME}Runtime/scripts/system/commons/logger.sh"

###############################################
###############################################
#            CONSTANTS DEFINITION
###############################################
###############################################

#----------------------------------------------
# DEFAULT VALUES
#----------------------------------------------
# Available Logger levels
LOG_LEVEL_DEBUG=debug
LOG_LEVEL_INFO=info
LOG_LEVEL_OFF=off

DEFAULT_LOG_LEVEL=${LOG_LEVEL_OFF}

# Enable COMPSs execution summary at the end of the execution
DEFAULT_SUMMARY=false

# Graph Generation
DEFAULT_GRAPH=false

# Monitoring
DEFAULT_MONITORING_INTERVAL=0

# External debugger options
DEFAULT_DEBUGGER=false
DEFAULT_DEBUGGER_PORT=9999

#----------------------------------------------
# ERROR MESSAGES
#----------------------------------------------


###############################################
###############################################
#      APP ANALYSIS HANDLING FUNCTIONS
###############################################
###############################################
#----------------------------------------------
# CHECK APP ANALISYS-RELATED ENV VARIABLES
#----------------------------------------------
check_analysis_env() {
  :
}


#----------------------------------------------
# CHECK ANALYSIS-RELATED SETUP values
#----------------------------------------------
check_analysis_setup () {
 
  if [ -z "${log_level}" ]; then
    log_level=${DEFAULT_LOG_LEVEL}
  fi

  if [ -z "${summary}" ]; then
    summary=${DEFAULT_SUMMARY}
  fi

  if [ -z "$graph" ]; then
    graph=${DEFAULT_GRAPH}
  fi

  # base_log_dir can be empty. Placement according to Runtime decision (currently, user's home folder)
  # specific_log_dir can be empty. If not specified uses base log dir

  # If specific_log_dir is provided, ensure it exists
  if [ -n "${specific_log_dir}" ]; then
    mkdir -p "${specific_log_dir}"
  fi

  if [ -z "$monitoring" ]; then
    monitoring=${DEFAULT_MONITORING_INTERVAL}
  else
    # If monitor has been activated trigger final graph generation and log_level = at least info
    graph=${DEFAULT_GRAPH_ARGUMENT}
    if [ "${log_level}" == "${DEFAULT_LOG_LEVEL}" ] || [ "${log_level}" == "${LOG_LEVEL_OFF}" ]; then
       log_level=${LOG_LEVEL_INFO}
    fi
  fi

    # Master log level
  if [ "${log_level}" == "${DEFAULT_LOG_LEVEL}" ]; then
    itlog4j_file="COMPSsMaster-log4j"
  else
    itlog4j_file="COMPSsMaster-log4j.${log_level}"
  fi

  if [ "${log_level}" == "${LOG_LEVEL_DEBUG}" ]; then
    export COMPSS_BINDINGS_DEBUG=1
  fi

  # External debugger
  if [ -z "$external_debugger" ]; then
    external_debugger=${DEFAULT_DEBUGGER}
  fi

  if [ "${external_debugger}" == "true" ]; then
    if [ -z "${external_debugger_port}" ]; then
      external_debugger_port=${DEFAULT_DEBUGGER_PORT}
    fi
  fi

  # jmx_port might be empty
}



#----------------------------------------------
# APPEND PROPERTIES TO FILE
#----------------------------------------------
append_analysis_jvm_options_to_file() {
  local jvm_options_file=${1}
    cat >> "${jvm_options_file}" << EOT
-Dcompss.baseLogDir=${base_log_dir}
-Dcompss.specificLogDir=${specific_log_dir}
-Dcompss.appLogDir=/tmp/${uuid}/
-Dlog4j.configurationFile=${COMPSS_HOME}/Runtime/configuration/log/${itlog4j_file}
-Dcompss.graph=${graph}
-Dcompss.monitor=${monitoring}
-Dcompss.summary=${summary}
EOT

  if [ "${external_debugger}" == "true" ]; then
    cat >> "${jvm_options_file}" << EOT
-Xdebug
-agentlib:jdwp=transport=dt_socket,address=${external_debugger_port},server=y,suspend=y
EOT
  fi

  if [ -n "${jmx_port}" ]; then
    cat >> "${jvm_options_file}" << EOT
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=${jmx_port}
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
EOT
    fi

# Uncomment block in case that you want to debug errors in JNI
##  if [ "${log_level}" == "debug" ]; then
##        cat >> "${jvm_options_file}" << EOT
##-Xcheck:jni
##-verbose:jni
##EOT
##  fi
}


#----------------------------------------------
# CLEAN ENV
#----------------------------------------------
clean_analysis_env () {
  : 
}
