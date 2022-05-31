/*
 *  Copyright 2002-2022 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package es.bsc.compss.types.execution;

import es.bsc.compss.types.execution.Invocation;
import es.bsc.compss.worker.COMPSsException;


public interface ExecutionListener {

    /**
     * Notifies the end of the given invocation with the given status.
     * 
     * @param invocation Task invocation.
     * @param success Whether the task was successful or not.
     * @param e COMPSsException for task groups.
     */
    public void notifyEnd(Invocation invocation, boolean success, COMPSsException e);

}
