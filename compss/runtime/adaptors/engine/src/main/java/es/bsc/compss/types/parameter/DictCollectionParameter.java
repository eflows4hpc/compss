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
package es.bsc.compss.types.parameter;

import es.bsc.compss.api.ParameterMonitor;
import es.bsc.compss.types.annotations.parameter.DataType;
import es.bsc.compss.types.annotations.parameter.Direction;
import es.bsc.compss.types.annotations.parameter.StdIOStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * The internal Dictionary (Collection) representation. A Dictionary Collection is a COMPSs Parameter objects which may
 * contain other COMPSs parameter objects. The object has an identifier by itself and points to other object identifiers
 * (which are the ones contained in it)
 */
public class DictCollectionParameter extends CollectiveParameter {

    /**
     * Serializable objects Version UID are 1L in all Runtime.
     */
    private static final long serialVersionUID = 1L;

    // Parameter objects of the collection contents
    private List<Parameter> parameters;
    private Map<Parameter, Parameter> mapping;


    /**
     * Default constructor. Intended to be called from COMPSsRuntimeImpl when gathering and compacting parameter
     * information fed from bindings or Java Loader
     * 
     * @param id identifier of the collection
     * @param map Parameters of the CollectionParameter
     * @param direction Direction of the collection
     * @param stream N/A (At least temporarily)
     * @param prefix N/A (At least temporarily)
     * @param name Name of the parameter in the user code
     * @param monitor object to notify to changes on the parameter
     * @see DependencyParameter
     * @see es.bsc.compss.api.impl.COMPSsRuntimeImpl
     * @see es.bsc.compss.components.impl.TaskAnalyser
     */
    public DictCollectionParameter(String id, Map<Parameter, Parameter> map, Direction direction, StdIOStream stream,
        String prefix, String name, String contentType, double weight, boolean keepRename, ParameterMonitor monitor) {

        // Type will always be DICT_COLLECTION_T, no need to pass it as a constructor parameter and wont be modified
        // Stream and prefix are still forwarded for possible, future uses
        super(DataType.DICT_COLLECTION_T, id, direction, stream, prefix, name, contentType, weight, keepRename,
            monitor);

        this.parameters = new ArrayList<>(map.size() * 2);
        for (Map.Entry<Parameter, Parameter> e : map.entrySet()) {
            this.parameters.add(e.getKey());
            this.parameters.add(e.getValue());
        }
        this.mapping = map;
    }

    @Override
    public String toString() {
        // String builder adds less overhead when creating a string
        StringBuilder sb = new StringBuilder();
        sb.append("DictCollectionParameter ").append(this.getCollectionId()).append("\n");
        sb.append("Name: ").append(getName()).append("\n");
        sb.append("Contents:\n");
        Iterator<Parameter> it = this.parameters.iterator();
        while (it.hasNext()) {
            Parameter key = it.next();
            Parameter value = it.next();
            sb.append("\t").append(key).append(" - ").append(value).append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<Parameter> getElements() {
        return this.parameters;
    }

    /**
     * Returns the map of the parameters.
     * 
     * @return List of the internal parameters of the collection.
     */
    public Map<Parameter, Parameter> getDictionary() {
        return this.mapping;
    }

    /**
     * Sets the internal parameters of the collection.
     * 
     * @param map New internal parameters of the collection.
     */
    public void setParameters(Map<Parameter, Parameter> map) {
        this.parameters = new ArrayList<>(map.size() * 2);
        for (Map.Entry<Parameter, Parameter> e : map.entrySet()) {
            this.parameters.add(e.getKey());
            this.parameters.add(e.getValue());
        }
        this.mapping = map;
    }
}
