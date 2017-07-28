#!/bin/bash 
 
  #############################################################
  # Name: storage_init.sh
  # Description: Storage API script for COMPSs
  # Parameters: <jobId>              Queue Job Id
  #             <masterNode>         COMPSs Master Node
  #             <storageMasterNode>  Node reserved for Storage Master Node (if needed)
  #             "<workerNodes>"      Nodes set as COMPSs workers
  #             <network>            Network type
  #             <storageProps>       Properties file for storage specific variables
  #############################################################


  #---------------------------------------------------------
  # ERROR CONSTANTS
  #---------------------------------------------------------
  ERROR_PROPS_FILE="Cannot find storage properties file"
  ERROR_GENERATE_CONF="Cannot generate conf file"
  ERROR_START_DATACLAY="Cannot start dataClay"


  #---------------------------------------------------------
  # HELPER FUNCTIONS
  #---------------------------------------------------------

  ####################
  # Function to display usage
  ####################
  usage() {
    local exitValue=$1

    echo " Usage: $0 <jobId> <masterNode> <storageMasterNode> \"<workerNodes>\" <network> <storageProps>"
    echo " "

    exit $exitValue
  }

  ####################
  # Function to display error
  ####################
  display_error() {
    local errorMsg=$1

    echo "ERROR: $errorMsg"
    exit 1
  }


  #---------------------------------------------------------
  # MAIN FUNCTIONS
  #---------------------------------------------------------

  ####################
  # Function to get args
  ####################
  get_args() {
    NUM_PARAMS=6

    # Check parameters
    if [ $# -eq 1 ]; then
      if [ "$1" == "usage" ]; then
        usage 0
      fi
    fi
    if [ $# -ne ${NUM_PARAMS} ]; then
      echo "Incorrect number of parameters"
      usage 1
    fi

    # Get parameters
    jobId=$1
    master_node=$2
    storage_master_node=$3
    worker_nodes=$4
    network=$5
    storageProps=$6
  }

  ####################
  # Function to check and arrange args
  ####################
  check_args() {
    # Check storage Props file exists
    if [ ! -f ${storageProps} ]; then
      # PropsFile doesn't exist
      display_error "${ERROR_PROPS_FILE}"
    fi
    source ${storageProps}

    # Convert network to suffix
    if [ "${network}" == "ethernet" ]; then
      network=""
    elif [ "${network}" == "infiniband" ]; then
      network="-ib0"
    elif [ "${network}" == "data" ]; then
      network=""
    fi
  }

  ####################
  # Function to log received arguments
  ####################
  log_args() {
    echo "--- STORAGE_INIT.SH ---"
    echo "Job ID:              $jobId"
    echo "Master Node:         $master_node"
    echo "Storage Master Node: $storage_master_node"
    echo "Worker Nodes:        $worker_nodes"
    echo "Network:             $network"
    echo "Storage Props:       $storageProps"
    echo "-----------------------"
  }
  ####################
  # Function to resolve a hostname to an IP
  ####################
  resolve_host_name() {
    echo `getent hosts $1 | awk '{ print $1 }'`
  }

  ####################
  # Function to check if an argument is an IP
  ####################
  function valid_ip() {
    local  ip=$1
    local  stat=1
    if [[ $ip =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
      OIFS=$IFS
      IFS='.'
      ip=($ip)
      IFS=$OIFS
      [[ ${ip[0]} -le 255 && ${ip[1]} -le 255 \
      && ${ip[2]} -le 255 && ${ip[3]} -le 255 ]]
      stat=$?
    fi
    return $stat
  }

  ####################
  # Given a node identifier, resolves it to an IP if it not already an IP
  ####################
  get_ip_address() {
    local output=$1;
    if ! valid_ip $1;
    then
      output=$(resolve_host_name $1);
    fi
    echo $output
  }

  ####################
  # Returns the command that must be executed in order to create a redis instance on a given location, on a given port
  ####################
  get_redis_deletion_command() {
    # $1 = host
    # $2 = path
    # $3 = port
    # It is more than likely that we do not need to explicitly kill the storage processes given the zombie-control
    # that many supercomputers have
    if [ "$REDIS_REMOTE_COMMAND" == "ssh" ];
    then
      echo "ssh $1 \"ps -ef | grep redis-server.*:$3 | grep -v grep | awk '{ print \\\$2 }' | xargs kill -9 \""
    elif [ "$REDIS_REMOTE_COMMAND" == "blaunch" ];
    then
      echo "BLAUNCH Pending to implement!"
    elif [ "$REDIS_REMOTE_COMMAND" == "srun" ];
    then
      echo "SRUN Pending to implement!"
    else
      echo "ERROR: \"$REDIS_REMOTE_COMMAND\" is not a valid deletion command or it is not supported"
      echo "Supported commands are: ssh, blaunch, and srun"
      exit 1
    fi
  }

  #---------------------------------------------------------
  # MAIN FUNCTIONS
  #---------------------------------------------------------
  REDIS_TEMPLATE="bind 0.0.0.0\nport REDIS_PORT\ncluster-enabled yes\ncluster-config-file nodes.conf\ncluster-node-timeout REDIS_NODE_TIMEOUT\nappendonly yes"
  # Default values
  # The idea is to replace these values by the desired ones in the --storage_conf file
  # The values below form a valid sample configuration anyway
  REDIS_HOME=/tmp/redis_cluster
  REDIS_PORT=6379
  REDIS_NODE_TIMEOUT=5000
  REDIS_REPLICAS=0
  REDIS_REMOTE_COMMAND=ssh

  STORAGE_HOME=$(dirname $0)/../

  get_args "$@"
  check_args
  log_args

  echo "REDIS CONFIGURATION PARAMETERS"
  echo "REDIS_HOME:           $REDIS_HOME"
  echo "REDIS_PORT:           $REDIS_PORT"
  echo "REDIS_NODE_TIMEOUT:   $REDIS_NODE_TIMEOUT"
  echo "REDIS_REPLICAS:       $REDIS_REPLICAS"
  echo "REDIS_REMOTE_COMMAND: $REDIS_REMOTE_COMMAND"
  echo "-----------------------"

  ############################
  ## STORAGE DEPENDENT CODE ##
  ############################

  # Pre-step: Resolve the nodes names to IPs (if needed)
  # This is due to the Redis limitation that imposes that it only works well when IPs are
  # passed. Given that we have no guarantee about the locations format (i.e: we do not know if they are
  # going to be hostnames of IPs) then we must check if we got an IP and, if not, resolve it
  # see get_ip_address, valid_ip, and resolve_host_name to see what is being done here
  storage_master_node=$(get_ip_address ${storage_master_node});
  worker_nodes=$(
    for worker_node in $worker_nodes
      do
        echo $(get_ip_address $worker_node)
      done
  );

  echo "RESOLVED REDIS NODES"
  echo "MASTER NODE: $storage_master_node"
  for worker_node in $worker_nodes
  do
    echo "WORKER NODE: $worker_node"
  done

  echo "-----------------------"

  # Compute the amount of needed redis instances
  # The amount of needed instances can be computed as follows:
  # max(3, (replicas + 1)*num_locations))
  # Redis needs at least three instances to work well as a cluster (otherwise they suggest you to switch to
  # standalone mode). Also, we need to have a Redis master in all of our nodes and we need their replicas too
  # In this script we are assuming that we have built the redis cluster with the same parameters and following the
  # same host-port assignation algorithm as in storage_init.sh
  all_instances_locations=("${storage_master_node} ${worker_nodes[@]}");
  num_locations=$(wc -w <<< "${all_instances_locations}");
  eff_locations=$num_locations
  if (($eff_locations < 3));
  then
    eff_locations=3;
  fi
  needed_instances=$(($((REDIS_REPLICAS + 1))*eff_locations));

  # Delete the Redis instances and their sandboxes
  # A Redis instance is located at a given host and listens to a given port
  # If we are forced to launch more than one instance then we are going to need more
  # ports than the given one to listen. Our choice is simple, if our original port is 6379 and it is not
  # available, then we will assign the port 6380, 6381... and so on. This idea is taken from the redis cluster
  # tutorial. We assume that these ports (alongside with these ports + 10000) are free. The default and official
  # port for Redis 6379, and the next official port is 6389 by some strange software, so it is generally safe
  # to establish ports this way
  # See https://en.wikipedia.org/wiki/List_of_TCP_and_UDP_port_numbers
  # A redis sandbox consists of a folder named after the port we are going to use that contains a redis.conf file
  # See REDIS_TEMPLATE and the line that seds it to redis_conf below to see what it is being done here
  current_instances=0;
  node_ids="";
  while (($current_instances < $needed_instances))
  do
    for instance_location in ${all_instances_locations}
    do
      if (($current_instances == $needed_instances))
      then
        break
      fi
      # Compute the port
      redis_port=$((REDIS_PORT + $((current_instances/num_locations))))
      # Replace the configuration template parameters with their values
      redis_conf=$(echo $REDIS_TEMPLATE | sed -s s/REDIS_PORT/$redis_port/ | sed -s s/REDIS_NODE_TIMEOUT/$REDIS_NODE_TIMEOUT/)
      # Delete the redis instance
      # This part is on an specific function because the needed command may vary from one queue system to another
      eval $(get_redis_deletion_command $instance_location $REDIS_HOME ${redis_port})
      current_instances=$((current_instances+1));
      node_name=${instance_location}:${redis_port}
      echo "DELETED REDIS INSTANCE IN $node_name"
    done
  done

  ############################
  ## END                    ##
  ############################
  exit

