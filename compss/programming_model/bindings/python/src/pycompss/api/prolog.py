#!/usr/bin/python
#
#  Copyright 2002-2021 Barcelona Supercomputing Center (www.bsc.es)
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
PyCOMPSs API - Prolog
==================
    Prolog definition for PyCOMPSs tasks.
"""

from functools import wraps

from pycompss.api.commons.constants import *
from pycompss.api.commons.decorator import PyCOMPSsDecorator
from pycompss.api.commons.decorator import keep_arguments
from pycompss.api.commons.decorator import resolve_fail_by_exit_value
from pycompss.api.commons.decorator import CORE_ELEMENT_KEY
from pycompss.runtime.task.core_element import CE
from pycompss.util.arguments import check_arguments

import pycompss.util.context as context


if __debug__:
    import logging

    logger = logging.getLogger(__name__)

MANDATORY_ARGUMENTS = {BINARY}
SUPPORTED_ARGUMENTS = {PARAMS, FAIL_BY_EXIT_VALUE}
DEPRECATED_ARGUMENTS = set()


class Prolog(PyCOMPSsDecorator):
    """
    Prolog decorator of the task. If defined, will execute the binary before the
    task execution on the worker. Should always be added on top of the 'task'
    definition.
    """

    __slots__ = []

    def __init__(self, *args, **kwargs):
        """ Store arguments passed to the decorator.

        self = itself.
        args = not used.
        kwargs = dictionary with the given binary and params strgins.

        :param args: Arguments
        :param kwargs: Keyword arguments
        """
        self.decorator_name = "".join(('@', Prolog.__name__.lower()))

        super(Prolog, self).__init__(self.decorator_name, *args, **kwargs)
        if self.scope:
            if __debug__:
                logger.debug("Init @prolog decorator...")

            # Check the arguments
            check_arguments(MANDATORY_ARGUMENTS,
                            DEPRECATED_ARGUMENTS,
                            SUPPORTED_ARGUMENTS | DEPRECATED_ARGUMENTS,
                            list(kwargs.keys()),
                            self.decorator_name)

    def __call__(self, user_function):
        # type: (typing.Callable) -> typing.Callable
        """
        Calling Prolog simply updates the CE and saves Prolog parameters.
        :param user_function: User function to be decorated.
        :return: Decorated dummy user function.
        """

        @wraps(user_function)
        def prolog_f(*args, **kwargs):
            return self.__decorator_body__(user_function, args, kwargs)

        prolog_f.__doc__ = user_function.__doc__
        return prolog_f

    def __decorator_body__(self, user_function, args, kwargs):
        if not self.scope:
            raise NotImplementedError

        if __debug__:
            logger.debug("Executing prolog wrapper.")

        if (context.in_master() or context.is_nesting_enabled()) \
                and not self.core_element_configured:
            self.__configure_core_element__(kwargs, user_function)

        with keep_arguments(args, kwargs, prepend_strings=True):
            # Call the method
            ret = user_function(*args, **kwargs)

        return ret

    def __configure_core_element__(self, kwargs, user_function):
        # type: (dict, ...) -> None
        """ Include the registering info related to @prolog.

        IMPORTANT! Updates self.kwargs[CORE_ELEMENT_KEY].

        :param kwargs: Keyword arguments received from call.
        :param user_function: Decorated function.
        :return: None
        """
        if __debug__:
            logger.debug("Configuring @prolog core element.")

        # Resolve the fail by exit value
        resolve_fail_by_exit_value(self.kwargs)

        binary = self.kwargs[BINARY]
        params = self.kwargs.get(PARAMS, UNASSIGNED)
        fail_by = self.kwargs.get(FAIL_BY_EXIT_VALUE)
        _prolog = [binary, params, fail_by]

        ce = kwargs.get(CORE_ELEMENT_KEY, CE())
        ce.set_prolog(_prolog)
        kwargs[CORE_ELEMENT_KEY] = ce
        # Set as configured
        self.core_element_configured = True


# ########################################################################### #
# ##################### MPI DECORATOR ALTERNATIVE NAME ###################### #
# ########################################################################### #

prolog = Prolog
