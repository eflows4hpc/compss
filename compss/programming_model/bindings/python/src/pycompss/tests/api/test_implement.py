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

from pycompss.api.implement import Implement
from pycompss.runtime.task.core_element import CE
from pycompss.api.commons.decorator import CORE_ELEMENT_KEY
import pycompss.util.context as context


def dummy_function(*args, **kwargs):  # noqa
    return 1


def test_implement_instantiation():
    context.set_pycompss_context(context.MASTER)
    my_implementation = Implement(source_class="s_class", method="s_method")
    assert my_implementation.decorator_name == "@implement", \
        "The decorator name must be @implement."


def test_implement_call():
    context.set_pycompss_context(context.MASTER)
    my_implementation = Implement(source_class="s_class", method="s_method")
    f = my_implementation(dummy_function)
    result = f()
    assert result == 1, \
        "Wrong expected result (should be 1)."


def test_implement_call_outside():
    context.set_pycompss_context(context.OUT_OF_SCOPE)
    my_implementation = Implement(source_class="s_class", method="s_method")
    f = my_implementation(dummy_function)
    thrown = False
    try:
        _ = f()
    except Exception:  # noqa
        thrown = True  # this is OK!
    assert thrown, \
        "The implement decorator did not raise an exception when invoked out of scope."  # noqa: E501


def test_implement_existing_core_element():
    context.set_pycompss_context(context.MASTER)
    my_implementation = Implement(source_class="s_class", method="s_method")
    f = my_implementation(dummy_function)
    # a higher level decorator would place the compss core element as follows:
    _ = f(compss_core_element=CE())
    assert CORE_ELEMENT_KEY not in my_implementation.kwargs, \
           "Core Element is not defined in kwargs dictionary."
