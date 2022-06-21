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

"""

import inspect

from functools import wraps

from pycompss.util.context import CONTEXT
from pycompss.api.commons.constants import INTERNAL_LABELS
from pycompss.api.commons.constants import LABELS
from pycompss.api.task import task
from pycompss.api.commons.decorator import keep_arguments
from pycompss.api.commons.decorator import resolve_fail_by_exit_value
from pycompss.api.commons.decorator import CORE_ELEMENT_KEY
from pycompss.runtime.task.core_element import CE
from pycompss.util.arguments import check_arguments
from pycompss.util.typing_helper import typing

if __debug__:
    import logging

    logger = logging.getLogger(__name__)

MANDATORY_ARGUMENTS = set()
SUPPORTED_ARGUMENTS = set()
DEPRECATED_ARGUMENTS = set()  # type: typing.Set[str]


class DataTransformation:  # pylint: disable=too-few-public-methods
    """
    """

    __slots__ = [
        "decorator_name",
        "args",
        "kwargs",
        "scope",
        "core_element",
        "user_function",
        "core_element_configured",
    ]

    def __init__(self, *args, **kwargs):
        """Store arguments passed to the decorator.

        self = itself.
        args = not used.
        kwargs = dictionary with the given DT arguments.

        :param args: Arguments
        :param kwargs: Keyword arguments
        """
        decorator_name = "".join(("@", DataTransformation.__name__.lower()))
        self.decorator_name = decorator_name
        self.args = args
        self.kwargs = kwargs
        self.scope = CONTEXT.in_pycompss()
        self.core_element = None  # type: typing.Any
        self.core_element_configured = False
        self.user_function = None
        if self.scope:
            # Check the arguments
            check_arguments(
                MANDATORY_ARGUMENTS,
                DEPRECATED_ARGUMENTS,
                SUPPORTED_ARGUMENTS | DEPRECATED_ARGUMENTS,
                list(kwargs.keys()),
                decorator_name,
            )

    def __call__(self, user_function: typing.Callable) -> typing.Callable:
        """Call Prolog simply updates the CE and saves Prolog parameters.

        :param user_function: User function to be decorated.
        :return: Decorated dummy user function.
        """

        @wraps(user_function)
        def dt_f(*args: typing.Any, **kwargs: typing.Any) -> typing.Any:
            if not self.scope:
                raise NotImplementedError

            if __debug__:
                logger.debug("Executing DT wrapper.")
            tmp = list(args)
            if (
                CONTEXT.in_master() or CONTEXT.is_nesting_enabled()
            ) and not self.core_element_configured:
                self.__configure_core_element__(user_function, tmp, kwargs)
            with keep_arguments(tuple(tmp), kwargs, prepend_strings=True):
                # Call the method
                ret = user_function(*tmp, **kwargs)

            return ret

        dt_f.__doc__ = user_function.__doc__
        return dt_f

    def __configure_core_element__(self, user_function, args: list, kwargs: dict) -> None:
        """
        IMPORTANT! Updates self.kwargs[CORE_ELEMENT_KEY].

        :param kwargs: Keyword arguments received from call.
        :return: None
        """
        dts = []
        self.user_function = user_function
        if __debug__:
            logger.debug("Configuring DT core element.")
        if not len(args):
            raise Exception
        elif "dt" in kwargs:
            tmp = kwargs.get("dt")
            if isinstance(tmp, DTObject):
                dts.append(tmp.extract())
            elif isinstance(tmp, list):
                dts = [obj.extract() for obj in tmp]
        else:
            if len(self.args) < 2:
                raise Exception
            dts.append((self.args[0], self.args[1], self.kwargs))

        for _dt in dts:
            self.apply_dt(*_dt, args, kwargs)

    # todo: comments
    def apply_dt(self, param_name, func, func_kwargs, args, kwargs):
        is_workflow = False
        if LABELS.is_workflow in func_kwargs:
            is_workflow = func_kwargs.pop(LABELS.is_workflow)
            is_workflow = is_workflow in [True, "True", "true", 1, "1"]

        p_value = None
        is_kwarg = param_name in kwargs
        if is_kwarg:
            p_value = kwargs.get(param_name)
        else:
            import inspect
            all_params = inspect.signature(self.user_function)
            keyz = all_params.parameters.keys()
            if param_name not in keyz:
                raise Exception("Wrong Param Name in DT")
            i = list(keyz).index(param_name)
            if i < len(args):
                p_value = args[i]
            else:
                p_value = all_params.parameters.get(param_name).default

        # no need to create a task if it's a workflow
        new_value = func(p_value, **func_kwargs)\
            if is_workflow else transform(p_value, func, **func_kwargs)
        if is_kwarg or i >= len(args):
            kwargs[param_name] = new_value
        else:
            args[i] = new_value


@task(returns=object)
def transform(data, function, **kwargs):
    return function(data, **kwargs)


class DTObject(object):

    def __init__(self, param_name, func, **func_kwargs):

        self.param_name = param_name
        self.func = func
        self.func_kwargs = func_kwargs

    def extract(self) -> tuple:
        return self.param_name, self.func, self.func_kwargs

# ########################################################################### #
# ############################# ALTERNATIVE NAME ############################ #
# ########################################################################### #


dt = DataTransformation  # pylint: disable=invalid-name
data_transformation = DataTransformation
dto = DTObject
