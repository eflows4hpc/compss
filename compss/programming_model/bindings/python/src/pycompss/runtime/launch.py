#!/usr/bin/python
#
#  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# -*- coding: utf-8 -*-

"""
PyCOMPSs Binding - Launch
=========================
    This file contains the __main__ method.
    It is called from the runcompss/enqueue_compss script with the user and
    environment parameters.
"""

# Imports
import os
import sys
import logging
import traceback
import argparse

# Project imports
import pycompss.util.context as context
from pycompss.runtime.binding import get_log_path
from pycompss.runtime.commons import set_temporary_directory
from pycompss.runtime.commons import set_object_conversion
from pycompss.runtime.commons import IS_PYTHON3
from pycompss.runtime.commons import RUNNING_IN_SUPERCOMPUTER
from pycompss.util.environment.configuration import prepare_environment
from pycompss.util.environment.configuration import prepare_loglevel_graph_for_monitoring  # noqa
from pycompss.util.environment.configuration import updated_variables_in_sc
from pycompss.util.environment.configuration import prepare_tracing_environment
from pycompss.util.environment.configuration import check_infrastructure_variables         # noqa
from pycompss.util.environment.configuration import create_init_config_file
from pycompss.util.logger.helpers import get_logging_cfg_file
from pycompss.util.logger.helpers import init_logging
from pycompss.util.serialization.serializer import SerializerException
from pycompss.util.warnings.modules import show_optional_module_warnings
from pycompss.util.interactive.flags import check_flags
from pycompss.util.interactive.flags import print_flag_issues
from pycompss.api.exceptions import COMPSsException

# Tracing imports
from pycompss.util.tracing.helpers import event
from pycompss.runtime.constants import APPLICATION_RUNNING_EVENT

# Storage imports
from pycompss.util.storages.persistent import master_init_storage
from pycompss.util.storages.persistent import master_stop_storage

# Streaming imports
from pycompss.streams.environment import init_streaming
from pycompss.streams.environment import stop_streaming

# Global variable also task-master decorator
APP_PATH = None

# Python version: to choose the appropriate log folder
if IS_PYTHON3:
    _PYTHON_VERSION = 3
else:
    _PYTHON_VERSION = 2


def parse_arguments():
    # type: () -> ...
    """ Parse PyCOMPSs arguments.

    :return: Argument's parser.
    """
    parser = argparse.ArgumentParser(
        description='PyCOMPSs application launcher')
    parser.add_argument('log_level',
                        help='Logging level [trace|debug|api|info|off]')
    parser.add_argument('tracing',
                        help='Tracing [-3(ARM DDT)|-2(ARM MAP)|-1(ScoreP)|0(Deactivated)|1(Basic)|2(Advanced)]')  # noqa: E501
    parser.add_argument('object_conversion',
                        help='Object_conversion [true|false]')
    parser.add_argument('storage_configuration',
                        help='Storage configuration [null|*]')
    parser.add_argument('streaming_backend',
                        help='Streaming Backend [null|*]')
    parser.add_argument('streaming_master_name',
                        help='Streaming Master Name [*]')
    parser.add_argument('streaming_master_port',
                        help='Streaming Master Port [*]')
    parser.add_argument('app_path',
                        help='Application path')
    return parser.parse_args()


def compss_main():
    # type: () -> None
    """ PyCOMPSs main function.

    General call:
    python $PYCOMPSS_HOME/pycompss/runtime/launch.py $log_level
           $PyObject_serialize $storage_conf $streaming_backend
           $streaming_master_name $streaming_master_port
           $fullAppPath $application_args

    :return: None
    """
    global APP_PATH

    # Let the Python binding know we are at master
    context.set_pycompss_context(context.MASTER)
    # Then we can import the appropriate start and stop functions from the API
    from pycompss.api.api import compss_start, compss_stop

    # See parse_arguments, defined above
    # In order to avoid parsing user arguments, we are going to remove user
    # args from sys.argv
    user_sys_argv = sys.argv[9:]
    sys.argv = sys.argv[:9]
    args = parse_arguments()
    # We are done, now sys.argv must contain user args only
    sys.argv = [args.app_path] + user_sys_argv

    # Get log_level
    log_level = args.log_level

    # Setup tracing
    tracing = int(args.tracing)

    # Start the runtime
    compss_start(log_level, tracing, False)

    # Get object_conversion boolean
    set_object_conversion(args.object_conversion == 'true')

    # Get storage configuration at master
    storage_conf = args.storage_configuration

    # Get application execution path
    APP_PATH = args.app_path

    # Setup logging
    binding_log_path = get_log_path()
    log_path = os.path.join(os.getenv('COMPSS_HOME'),
                            'Bindings',
                            'python',
                            str(_PYTHON_VERSION),
                            'log')
    set_temporary_directory(binding_log_path)
    logging_cfg_file = get_logging_cfg_file(log_level)
    init_logging(os.path.join(log_path, logging_cfg_file), binding_log_path)
    logger = logging.getLogger("pycompss.runtime.launch")

    # Get JVM options
    # jvm_opts = os.environ['JVM_OPTIONS_FILE']
    # from pycompss.util.jvm.parser import convert_to_dict
    # opts = convert_to_dict(jvm_opts)
    # storage_conf = opts.get('-Dcompss.storage.conf')

    exit_code = 0
    try:
        if __debug__:
            logger.debug('--- START ---')
            logger.debug('PyCOMPSs Log path: %s' % binding_log_path)

        # Start persistent storage
        persistent_storage = master_init_storage(storage_conf, logger)

        # Start streaming
        streaming = init_streaming(args.streaming_backend,
                                   args.streaming_master_name,
                                   args.streaming_master_port)

        # Show module warnings
        if __debug__:
            show_optional_module_warnings()

        # MAIN EXECUTION
        with event(APPLICATION_RUNNING_EVENT, master=True):
            if IS_PYTHON3:
                with open(APP_PATH) as f:
                    exec(compile(f.read(), APP_PATH, 'exec'), globals())
            else:
                execfile(APP_PATH, globals())  # MAIN EXECUTION

        # Stop streaming
        if streaming:
            stop_streaming()

        # Stop persistent storage
        if persistent_storage:
            master_stop_storage(logger)

        # End
        if __debug__:
            logger.debug('--- END ---')
    except SystemExit as e:
        if e.code != 0:
            print('[ ERROR ]: User program ended with exitcode %s.' % e.code)
            print('\t\tShutting down runtime...')
            exit_code = e.code
    except SerializerException:
        exit_code = 1
        # If an object that can not be serialized has been used as a parameter.
        print("[ ERROR ]: Serialization exception")
        exc_type, exc_value, exc_traceback = sys.exc_info()
        lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
        for line in lines:
            if APP_PATH in line:
                print('[ ERROR ]: In: %s', line)
        exit_code = 1
    except COMPSsException as e:
        # Any other exception occurred
        print("[ ERROR ]: A COMPSs exception occurred: " + str(e))
        traceback.print_exc()
        exit_code = 0  # COMPSs exception is not considered an error
    except Exception as e:
        # Any other exception occurred
        print("[ ERROR ]: An exception occurred: " + str(e))
        traceback.print_exc()
        exit_code = 1
    finally:
        # Stop runtime
        compss_stop(exit_code)
        sys.stdout.flush()
        sys.stderr.flush()
        sys.exit(exit_code)
    # --- Execution finished ---


# ###################################################### #
#        ------  FOR EXTERNAL EXECUTION ------           #
# Starts a new COMPSs runtime and calls the application. #
# ###################################################### #

def launch_pycompss_application(app,
                                func,
                                log_level='off',                 # type: str
                                o_c=False,                       # type: bool
                                debug=False,                     # type: bool
                                graph=False,                     # type: bool
                                trace=False,                     # type: bool
                                monitor=None,                    # type: int
                                project_xml=None,                # type: str
                                resources_xml=None,              # type: str
                                summary=False,                   # type: bool
                                task_execution='compss',         # type: str
                                storage_impl=None,               # type: str
                                storage_conf=None,               # type: str
                                streaming_backend=None,          # type: str
                                streaming_master_name=None,      # type: str
                                streaming_master_port=None,      # type: str
                                task_count=50,                   # type: int
                                app_name=None,                   # type: str
                                uuid=None,                       # type: str
                                base_log_dir=None,               # type: str
                                specific_log_dir=None,           # type: str
                                extrae_cfg=None,                 # type: str
                                comm='NIO',                      # type: str
                                conn='es.bsc.compss.connectors.DefaultSSHConnector',                       # noqa: E501
                                master_name='',                  # type: str
                                master_port='',                  # type: str
                                scheduler='es.bsc.compss.scheduler.loadbalancing.LoadBalancingScheduler',  # noqa: E501
                                jvm_workers='-Xms1024m,-Xmx1024m,-Xmn400m',
                                cpu_affinity='automatic',        # type: str
                                gpu_affinity='automatic',        # type: str
                                fpga_affinity='automatic',       # type: str
                                fpga_reprogram='',               # type: str
                                profile_input='',                # type: str
                                profile_output='',               # type: str
                                scheduler_config='',             # type: str
                                external_adaptation=False,       # type: bool
                                propagate_virtual_environment=True,  # type: bool
                                mpi_worker=False,                # type: bool
                                *args, **kwargs
                                ):
    # type: (...) -> None
    """ Launch PyCOMPSs application from function.

    :param app: Application path
    :param func: Function
    :param log_level: Logging level [ 'trace'|'debug'|'info'|'api'|'off' ]
                      (default: 'off')
    :param o_c: Objects to string conversion [ True | False ] (default: False)
    :param debug: Debug mode [ True | False ] (default: False)
                  (overrides log_level)
    :param graph: Generate graph [ True | False ] (default: False)
    :param trace: Generate trace
                  [ True | False | 'scorep' | 'arm-map' | 'arm-ddt']
                  (default: False)
    :param monitor: Monitor refresh rate (default: None)
    :param project_xml: Project xml file path
    :param resources_xml: Resources xml file path
    :param summary: Execution summary [ True | False ] (default: False)
    :param task_execution: Task execution (default: 'compss')
    :param storage_impl: Storage implementation path
    :param storage_conf: Storage configuration file path
    :param streaming_backend: Streaming backend (default: None)
    :param streaming_master_name: Streaming master name (default: None)
    :param streaming_master_port: Streaming master port (default: None)
    :param task_count: Task count (default: 50)
    :param app_name: Application name (default: Interactive_date)
    :param uuid: UUId
    :param base_log_dir: Base logging directory
    :param specific_log_dir: Specific logging directory
    :param extrae_cfg: Extrae configuration file path
    :param comm: Communication library (default: NIO)
    :param conn: Connector (default: DefaultSSHConnector)
    :param master_name: Master Name (default: '')
    :param master_port: Master port (default: '')
    :param scheduler: Scheduler (default:
                  es.bsc.compss.scheduler.loadbalancing.LoadBalancingScheduler)
    :param jvm_workers: Java VM parameters
                        (default: '-Xms1024m,-Xmx1024m,-Xmn400m')
    :param cpu_affinity: CPU Core affinity (default: 'automatic')
    :param gpu_affinity: GPU Core affinity (default: 'automatic')
    :param fpga_affinity: FPA Core affinity (default: 'automatic')
    :param fpga_reprogram: FPGA repogram command (default: '')
    :param profile_input: Input profile  (default: '')
    :param profile_output: Output profile  (default: '')
    :param scheduler_config: Scheduler configuration  (default: '')
    :param external_adaptation: External adaptation [ True | False ]
                                (default: False)
    :param propagate_virtual_environment: Propagate virtual environment
                                          [ True | False ] (default: False)
    :param mpi_worker: Use the MPI worker [ True | False ] (default: False)
    :param args: Positional arguments
    :param kwargs: Named arguments
    :return: Execution result
    """
    # Let the Python binding know we are at master
    context.set_pycompss_context(context.MASTER)
    # Then we can import the appropriate start and stop functions from the API
    from pycompss.api.api import compss_start, compss_stop

    ##############################################################
    # INITIALIZATION
    ##############################################################

    # Initial dictionary with the user defined parameters
    all_vars = {'log_level': log_level,
                'debug': debug,
                'o_c': o_c,
                'graph': graph,
                'trace': trace,
                'monitor': monitor,
                'project_xml': project_xml,
                'resources_xml': resources_xml,
                'summary': summary,
                'task_execution': task_execution,
                'storage_impl': storage_impl,
                'storage_conf': storage_conf,
                'streaming_backend': streaming_backend,
                'streaming_master_name': streaming_master_name,
                'streaming_master_port': streaming_master_port,
                'task_count': task_count,
                'app_name': app_name,
                'uuid': uuid,
                'base_log_dir': base_log_dir,
                'specific_log_dir': specific_log_dir,
                'extrae_cfg': extrae_cfg,
                'comm': comm,
                'conn': conn,
                'master_name': master_name,
                'master_port': master_port,
                'scheduler': scheduler,
                'jvm_workers': jvm_workers,
                'cpu_affinity': cpu_affinity,
                'gpu_affinity': gpu_affinity,
                'fpga_affinity': fpga_affinity,
                'fpga_reprogram': fpga_reprogram,
                'profile_input': profile_input,
                'profile_output': profile_output,
                'scheduler_config': scheduler_config,
                'external_adaptation': external_adaptation,
                'propagate_virtual_environment': propagate_virtual_environment,
                'mpi_worker': mpi_worker}

    # Check the provided flags
    flags, issues = check_flags(all_vars)
    if not flags:
        print_flag_issues(issues)
        return None

    # Prepare the environment
    env_vars = prepare_environment(False, o_c, storage_impl, app,
                                   debug, trace, mpi_worker)
    all_vars.update(env_vars)

    monitoring_vars = prepare_loglevel_graph_for_monitoring(monitor,
                                                            graph,
                                                            debug,
                                                            log_level)
    all_vars.update(monitoring_vars)

    if RUNNING_IN_SUPERCOMPUTER:
        updated_vars = updated_variables_in_sc()
        all_vars.update(updated_vars)

    to_update = prepare_tracing_environment(all_vars['trace'],
                                            all_vars['extrae_lib'],
                                            all_vars['ld_library_path'])
    all_vars['trace'], all_vars['ld_library_path'] = to_update

    inf_vars = check_infrastructure_variables(all_vars['project_xml'],
                                              all_vars['resources_xml'],
                                              all_vars['compss_home'],
                                              all_vars['app_name'],
                                              all_vars['file_name'],
                                              all_vars['external_adaptation'])
    all_vars.update(inf_vars)

    create_init_config_file(**all_vars)

    ##############################################################
    # RUNTIME START
    ##############################################################

    # Runtime start
    compss_start(log_level, all_vars['trace'], True)

    # Setup logging
    binding_log_path = get_log_path()
    log_path = os.path.join(all_vars['compss_home'],
                            'Bindings',
                            'python',
                            str(all_vars['major_version']),
                            'log')
    set_temporary_directory(binding_log_path)
    logging_cfg_file = get_logging_cfg_file(log_level)
    init_logging(os.path.join(log_path, logging_cfg_file), binding_log_path)
    logger = logging.getLogger("pycompss.runtime.launch")

    logger.debug('--- START ---')
    logger.debug('PyCOMPSs Log path: %s' % log_path)

    logger.debug("Starting storage")
    persistent_storage = master_init_storage(all_vars['storage_conf'], logger)

    logger.debug("Starting streaming")
    streaming = init_streaming(all_vars['streaming_backend'],
                               all_vars['streaming_master_name'],
                               all_vars['streaming_master_port'])

    saved_argv = sys.argv
    sys.argv = args
    # Execution:
    with event(APPLICATION_RUNNING_EVENT, master=True):
        if func is None or func == '__main__':
            if IS_PYTHON3:
                exec(open(app).read())
            else:
                execfile(app)  # noqa
            result = None
        else:
            if IS_PYTHON3:
                import importlib.util
                spec = importlib.util.spec_from_file_location(all_vars['file_name'], app)  # noqa: E501
                imported_module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(imported_module)  # noqa
            else:
                import imp  # noqa
                imported_module = imp.load_source(all_vars['file_name'], app)  # noqa
            method_to_call = getattr(imported_module, func)
            result = method_to_call(*args, **kwargs)
    # Recover the system arguments
    sys.argv = saved_argv

    # Stop streaming
    if streaming:
        stop_streaming()

    # Stop persistent storage
    if persistent_storage:
        master_stop_storage(logger)

    logger.debug('--- END ---')

    ##############################################################
    # RUNTIME STOP
    ##############################################################

    # Stop runtime
    compss_stop()

    return result


if __name__ == '__main__':
    """
    This is the PyCOMPSs entry point.
    """
    compss_main()
