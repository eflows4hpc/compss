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
package es.bsc.compss.types.data.transferable;

import es.bsc.compss.types.annotations.parameter.DataType;
import es.bsc.compss.types.data.Transferable;


public class SafeCopyTransferable implements Transferable {

    private Object dataSource;
    private String dataTarget;
    private DataType type;


    public SafeCopyTransferable(DataType dt) {
        this.type = dt;
    }

    public SafeCopyTransferable() {
        this.type = DataType.FILE_T;
    }

    @Override
    public Object getDataSource() {
        return dataSource;
    }

    @Override
    public void setDataSource(Object dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getDataTarget() {
        return dataTarget;
    }

    @Override
    public void setDataTarget(String target) {
        this.dataTarget = target;
    }

    @Override
    public DataType getType() {
        return type;
    }

    @Override
    public boolean isSourcePreserved() {
        // Only invoked to transfer data values from a remote node for saving them on the master.
        // Remote data is always copied
        return true;
    }
}
