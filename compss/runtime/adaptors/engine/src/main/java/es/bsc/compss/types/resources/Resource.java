/*
 *  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
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
package es.bsc.compss.types.resources;

import es.bsc.compss.exceptions.InitNodeException;
import es.bsc.compss.exceptions.UnstartedNodeException;
import es.bsc.compss.types.COMPSsNode;
import es.bsc.compss.types.TaskDescription;
import es.bsc.compss.types.annotations.parameter.DataType;
import es.bsc.compss.types.data.LogicalData;
import es.bsc.compss.types.data.Transferable;
import es.bsc.compss.types.data.listener.EventListener;
import es.bsc.compss.types.data.location.DataLocation;
import es.bsc.compss.types.implementations.Implementation;
import es.bsc.compss.types.job.Job;
import es.bsc.compss.types.job.JobListener;
import es.bsc.compss.types.uri.MultiURI;
import es.bsc.compss.types.uri.SimpleURI;

import java.util.List;
import java.util.Set;


public interface Resource extends Comparable<Resource> {

    /**
     * Starts a resource execution.
     *
     * @throws InitNodeException Error starting a resource
     */
    public void start() throws InitNodeException;

    /**
     * Returns all the LogicalData stored in the host.
     *
     * @return
     */
    public Set<LogicalData> getAllDataFromHost();

    /**
     * Adds a new LogicalData available in the host.
     *
     * @param ld Logical data to add
     */
    public void addLogicalData(LogicalData ld);

    /**
     * Marks a file as obsolete.
     *
     * @param obsolete logical data to mark as obsolete
     */
    public void addObsolete(LogicalData obsolete);

    /**
     * Gets the list of obsolete files.
     *
     * @return List of logicalData objects
     */
    public LogicalData[] pollObsoletes();

    /**
     * Clears the list of obsolete files.
     */
    public void clearObsoletes();

    /**
     * Returns the node name.
     *
     * @return
     */
    public String getName();

    /**
     * Returns the node associated to the resource.
     *
     * @return
     */
    public COMPSsNode getNode();

    /**
     * Returns the internal URI representation of the given MultiURI.
     *
     * @param u Multi- URI
     * @throws UnstartedNodeException Error node not started
     */
    public void setInternalURI(MultiURI u) throws UnstartedNodeException;

    /**
     * Creates a new Job from a task in the resource.
     *
     * @param taskId Task Identifier
     * @param taskParams Task description
     * @param impl Task Implementation
     * @param slaveWorkersNodeNames List of slave resources assigned in a multi-node execution
     * @param listener Listener to notify job events
     * @return
     */
    public Job<?> newJob(int taskId, TaskDescription taskParams, Implementation impl,
        List<String> slaveWorkersNodeNames, JobListener listener);

    /**
     * Retrieves a given data.
     *
     * @param dataId Data name/identifier
     * @param tgtDataId Target data name/identifier
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(String dataId, String tgtDataId, Transferable reason, EventListener listener);

    /**
     * Retrieves a given data.
     *
     * @param ld Source logical data
     * @param tgtData Target logical data
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(LogicalData ld, LogicalData tgtData, Transferable reason, EventListener listener);

    /**
     * Retrieves a given data.
     *
     * @param dataId Data name/identifier
     * @param newName Target data new name
     * @param tgtDataId Target data identifier
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(String dataId, String newName, String tgtDataId, Transferable reason, EventListener listener);

    /**
     * Retrieves a given data.
     *
     * @param dataId Data name/identifier
     * @param newName Target data new name
     * @param tgtData Target logical data
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(String dataId, String newName, LogicalData tgtData, Transferable reason,
        EventListener listener);

    /**
     * Retrieves a given data.
     *
     * @param ld Source logical data
     * @param newName Target data new name
     * @param tgtData Target logical data
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(LogicalData ld, String newName, LogicalData tgtData, Transferable reason,
        EventListener listener);

    /**
     * Retrieves a given data.
     *
     * @param dataId Data name/identifier
     * @param target Target location
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(String dataId, DataLocation target, Transferable reason, EventListener listener);

    public void getData(String dataId, DataLocation target, String tgtDataId, Transferable reason,
        EventListener listener);

    /**
     * Retrieves a given data.
     *
     * @param dataId Data name/identifier
     * @param target Target location
     * @param tgtData Target logical data
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(String dataId, DataLocation target, LogicalData tgtData, Transferable reason,
        EventListener listener);

    /**
     * Retrieves a given data.
     *
     * @param srcData Source logical data
     * @param target Target location
     * @param tgtData Target logical data
     * @param reason Transferable action how requested the data retrieve
     * @param listener Listener to notify operation events
     */
    public void getData(LogicalData srcData, DataLocation target, LogicalData tgtData, Transferable reason,
        EventListener listener);

    /**
     * Enforces the retrieval of data whose transfer is supposed to be already ordered.
     *
     * @param t Data already requested to be retrieved
     * @param listener Listener to notify operation events
     */
    public void enforceDataObtaning(Transferable t, EventListener listener);

    /**
     * Returns the complete remote path of a given data .
     *
     * @param type Data type
     * @param name Data name/identifier
     * @return
     */
    public SimpleURI getCompleteRemotePath(DataType type, String name);

    /**
     * Retrieves all the data from the Resource.
     *
     * @param saveUniqueData Flag to indicate to save unique data
     */
    public void retrieveData(boolean saveUniqueData);

    /**
     * Deletes the intermediate data.
     */
    public void deleteIntermediate();

    /**
     * Stops the resource.
     *
     * @param sl Listener to notify operation events
     */
    public void stop(ShutdownListener sl);

    /**
     * Returns the Resource type.
     *
     * @return
     */
    public ResourceType getType();

}
