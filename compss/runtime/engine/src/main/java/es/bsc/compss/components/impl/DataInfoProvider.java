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
package es.bsc.compss.components.impl;

import es.bsc.compss.comm.Comm;
import es.bsc.compss.exceptions.CommException;
import es.bsc.compss.log.Loggers;
import es.bsc.compss.types.Application;
import es.bsc.compss.types.BindingObject;
import es.bsc.compss.types.data.DataAccessId;
import es.bsc.compss.types.data.DataInfo;
import es.bsc.compss.types.data.DataInstanceId;
import es.bsc.compss.types.data.DataVersion;
import es.bsc.compss.types.data.FileInfo;
import es.bsc.compss.types.data.LogicalData;
import es.bsc.compss.types.data.ResultFile;
import es.bsc.compss.types.data.Transferable;
import es.bsc.compss.types.data.accessid.RAccessId;
import es.bsc.compss.types.data.accessid.RWAccessId;
import es.bsc.compss.types.data.accessid.WAccessId;
import es.bsc.compss.types.data.accessparams.AccessParams;
import es.bsc.compss.types.data.accessparams.AccessParams.AccessMode;
import es.bsc.compss.types.data.accessparams.DataParams;
import es.bsc.compss.types.data.location.BindingObjectLocation;
import es.bsc.compss.types.data.location.DataLocation;
import es.bsc.compss.types.data.location.PersistentLocation;
import es.bsc.compss.types.data.location.ProtocolType;
import es.bsc.compss.types.data.operation.BindingObjectTransferable;
import es.bsc.compss.types.data.operation.DirectoryTransferable;
import es.bsc.compss.types.data.operation.FileTransferable;
import es.bsc.compss.types.data.operation.ObjectTransferable;
import es.bsc.compss.types.data.operation.OneOpWithSemListener;
import es.bsc.compss.types.data.operation.ResultListener;
import es.bsc.compss.types.request.ap.TransferBindingObjectRequest;
import es.bsc.compss.types.request.ap.TransferObjectRequest;
import es.bsc.compss.types.tracing.TraceEvent;
import es.bsc.compss.types.uri.MultiURI;
import es.bsc.compss.types.uri.SimpleURI;
import es.bsc.compss.util.ErrorManager;
import es.bsc.compss.util.Tracer;
import es.bsc.compss.util.serializers.Serializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import storage.StorageException;
import storage.StorageItf;


/**
 * Component to handle the specific data structures such as file names, versions, renamings and values.
 */
public class DataInfoProvider {

    // Constants definition
    private static final String RES_FILE_TRANSFER_ERR = "Error transferring result files";

    // Map: hash code -> object identifier
    private final TreeMap<Integer, Integer> codeToId;

    // Map: file identifier -> file information
    private TreeMap<Integer, DataInfo> idToData;
    // Set: Object values available for main code
    private TreeSet<String> valuesOnMain; // TODO: Remove obsolete from here

    // Component logger - No need to configure, ProActive does
    private static final Logger LOGGER = LogManager.getLogger(Loggers.DIP_COMP);
    private static final boolean DEBUG = LOGGER.isDebugEnabled();


    /**
     * New Data Info Provider instance.
     */
    public DataInfoProvider() {
        this.codeToId = new TreeMap<>();
        this.idToData = new TreeMap<>();
        this.valuesOnMain = new TreeSet<>();

        LOGGER.info("Initialization finished");
    }

    public Integer getObjectDataId(int code) {
        return this.codeToId.get(code);
    }

    public void registerObjectDataId(int code, int dataId) {
        this.codeToId.put(code, dataId);
    }

    public Integer deregisterObjectDataId(int code) {
        return this.codeToId.remove(code);
    }

    private DataInfo registerData(DataParams data) {
        DataInfo dInfo = data.createDataInfo(this);
        this.idToData.put(dInfo.getDataId(), dInfo);
        return dInfo;
    }

    private void deregisterData(DataInfo di) {
        int dataId = di.getDataId();
        idToData.remove(dataId);
        di.deleted(this);
    }

    /**
     * Registers the remote data.
     *
     * @param internalData local value
     * @param externalData Existing LogicalData to bind the value
     */
    public void registerRemoteDataSources(DataParams internalData, String externalData) {
        DataInfo dInfo;
        Integer dId = internalData.getDataId(this);
        if (dId == null) {
            if (DEBUG) {
                LOGGER.debug("Registering Remote data on DIP: " + internalData.getDescription());
            }
            dInfo = registerData(internalData);
        } else {
            dInfo = idToData.get(dId);
        }
        if (externalData != null && dInfo != null) {
            String existingRename = dInfo.getCurrentDataVersion().getDataInstanceId().getRenaming();
            try {
                Comm.linkData(externalData, existingRename);
            } catch (CommException ce) {
                ErrorManager.error("Could not link the newly created data for " + internalData.getDescription()
                    + " with data " + externalData, ce);
            }
        }
    }

    /**
     * Obtains the last value produced for a data.
     *
     * @param internalData local value
     * @return last data produced for that value.
     */
    public LogicalData getDataLastVersion(DataParams internalData) {
        Integer dId = internalData.getDataId(this);
        if (dId != null) {
            DataInfo fileInfo = this.idToData.get(dId);
            if (fileInfo != null) {
                return fileInfo.getCurrentDataVersion().getDataInstanceId().getData();
            }
        }
        return null;
    }

    /**
     * DataAccess interface: registers a new data access.
     *
     * @param access Access Parameters.
     * @return The registered access Id.
     */
    public DataAccessId registerDataAccess(AccessParams access) {
        DataInfo dInfo;
        Integer dId = access.getDataId(this);
        if (dId == null) {
            if (DEBUG) {
                LOGGER.debug("FIRST access to " + access.getDataDescription());
            }
            dInfo = registerData(access.getData());
            access.registeredAsFirstVersionForData(dInfo);
        } else {
            dInfo = idToData.get(dId);
            if (dInfo != null) {
                if (DEBUG) {
                    LOGGER.debug("Another access to " + access.getDataDescription());
                }
            } else {
                ErrorManager.warn(access.getDataDescription() + " was accessed but the file information not found. "
                    + "Maybe it has been previously canceled");
                return null;
            }
        }

        DataAccessId daId = willAccess(access, dInfo);
        return daId;
    }

    /**
     * Marks the access from the main as finished.
     * 
     * @param access access being completed
     */
    public void finishDataAccess(AccessParams access) {
        Integer dId = access.getDataId(this);
        // First access to this file
        if (dId == null) {
            LOGGER.warn(access.getDataDescription() + " has not been accessed before");
            return;
        }
        DataInfo dInfo = this.idToData.get(dId);
        DataAccessId daid = getAccess(access.getMode(), dInfo);
        if (daid == null) {
            LOGGER.warn(access.getDataDescription() + " has not been accessed before");
            return;
        }
        dataHasBeenAccessed(daid);
    }

    private DataAccessId willAccess(AccessParams access, DataInfo di) {
        // Version management
        DataAccessId daId = null;
        switch (access.getMode()) {
            case C:
            case R:
                di.willBeRead();
                daId = new RAccessId(di.getCurrentDataVersion());
                if (DEBUG) {
                    StringBuilder sb = new StringBuilder("");
                    sb.append("Access:").append("\n");
                    sb.append("  * Type: R").append("\n");
                    sb.append("  * Read Datum: d").append(daId.getDataId()).append("v")
                        .append(((RAccessId) daId).getRVersionId()).append("\n");
                    LOGGER.debug(sb.toString());
                }
                break;

            case W:
                di.willBeWritten();
                daId = new WAccessId(di.getCurrentDataVersion());
                if (DEBUG) {
                    StringBuilder sb = new StringBuilder("");
                    sb.append("Access:").append("\n");
                    sb.append("  * Type: W").append("\n");
                    sb.append("  * Write Datum: d").append(daId.getDataId()).append("v")
                        .append(((WAccessId) daId).getWVersionId()).append("\n");
                    LOGGER.debug(sb.toString());
                }
                break;

            case CV:
            case RW:
                di.willBeRead();
                DataVersion readInstance = di.getCurrentDataVersion();
                di.willBeWritten();
                DataVersion writtenInstance = di.getCurrentDataVersion();
                daId = new RWAccessId(readInstance, writtenInstance);
                if (DEBUG) {
                    StringBuilder sb = new StringBuilder("");
                    sb.append("Access:").append("\n");
                    sb.append("  * Type: RW").append("\n");
                    sb.append("  * Read Datum: d").append(daId.getDataId()).append("v")
                        .append(((RWAccessId) daId).getRVersionId()).append("\n");
                    sb.append("  * Write Datum: d").append(daId.getDataId()).append("v")
                        .append(((RWAccessId) daId).getWVersionId()).append("\n");
                    LOGGER.debug(sb.toString());
                }
                break;
        }
        access.externalRegister();
        return daId;
    }

    private DataAccessId getAccess(AccessMode mode, DataInfo di) {
        // Version management
        DataAccessId daId = null;
        DataVersion currentInstance = di.getCurrentDataVersion();
        if (currentInstance != null) {
            switch (mode) {
                case C:
                case R:
                    daId = new RAccessId(currentInstance);
                    break;
                case W:
                    daId = new WAccessId(di.getCurrentDataVersion());
                    break;
                case CV:
                case RW:
                    DataVersion readInstance = di.getPreviousDataVersion();
                    if (readInstance != null) {
                        daId = new RWAccessId(readInstance, currentInstance);
                    } else {
                        LOGGER.warn("Previous instance for data" + di.getDataId() + " is null.");
                    }
                    break;
            }
        } else {
            LOGGER.warn("Current instance for data" + di.getDataId() + " is null.");
        }
        return daId;
    }

    /**
     * Removes the versions associated with the given DataAccessId {@code dAccId} to if the task was canceled or not.
     *
     * @param dAccId DataAccessId.
     */
    public void dataAccessHasBeenCanceled(DataAccessId dAccId, boolean keepModified) {
        Integer dataId = dAccId.getDataId();
        DataInfo di = this.idToData.get(dataId);
        if (di != null) {
            Integer rVersionId;
            Integer wVersionId;
            boolean deleted = false;
            switch (dAccId.getDirection()) {
                case C:
                case R:
                    rVersionId = ((RAccessId) dAccId).getReadDataInstance().getVersionId();
                    deleted = di.canceledReadVersion(rVersionId);
                    break;
                case CV:
                case RW:
                    rVersionId = ((RWAccessId) dAccId).getReadDataInstance().getVersionId();
                    wVersionId = ((RWAccessId) dAccId).getWrittenDataInstance().getVersionId();
                    if (keepModified) {
                        di.versionHasBeenRead(rVersionId);
                        // read data version can be removed
                        di.tryRemoveVersion(rVersionId);
                        deleted = di.versionHasBeenWritten(wVersionId);
                    } else {
                        di.canceledReadVersion(rVersionId);
                        deleted = di.canceledWriteVersion(wVersionId);
                    }
                    break;
                default:// case W:
                    wVersionId = ((WAccessId) dAccId).getWrittenDataInstance().getVersionId();
                    deleted = di.canceledWriteVersion(wVersionId);
                    break;
            }

            if (deleted) {
                deregisterData(di);
            }
        } else {
            LOGGER.debug("Access of Data" + dAccId.getDataId() + " in Mode " + dAccId.getDirection().name()
                + " can not be cancelled because do not exist in DIP.");
        }
    }

    /**
     * Marks that a given data {@code dAccId} has been accessed.
     *
     * @param dAccId DataAccessId.
     */
    public void dataHasBeenAccessed(DataAccessId dAccId) {
        Integer dataId = dAccId.getDataId();
        DataInfo di = this.idToData.get(dataId);
        if (di != null) {
            Integer rVersionId;
            Integer wVersionId;
            boolean deleted = false;
            switch (dAccId.getDirection()) {
                case C:
                case R:
                    rVersionId = ((RAccessId) dAccId).getReadDataInstance().getVersionId();
                    deleted = di.versionHasBeenRead(rVersionId);
                    break;
                case CV:
                case RW:
                    rVersionId = ((RWAccessId) dAccId).getReadDataInstance().getVersionId();
                    di.versionHasBeenRead(rVersionId);
                    // read data version can be removed
                    di.tryRemoveVersion(rVersionId);
                    wVersionId = ((RWAccessId) dAccId).getWrittenDataInstance().getVersionId();
                    deleted = di.versionHasBeenWritten(wVersionId);
                    break;
                default:// case W:
                    wVersionId = ((WAccessId) dAccId).getWrittenDataInstance().getVersionId();
                    Integer prevVersionId = wVersionId - 1;
                    di.tryRemoveVersion(prevVersionId);
                    deleted = di.versionHasBeenWritten(wVersionId);
                    break;
            }

            if (deleted) {
                deregisterData(di);
            }
        } else {
            LOGGER.warn("Access of Data" + dAccId.getDataId() + " in Mode " + dAccId.getDirection().name()
                + "can not be mark as accessed because do not exist in DIP.");
        }
    }

    /**
     * Returns whether a given location has been accessed or not.
     *
     * @param app Application accessing the data
     * @param loc Location.
     * @return {@code true} if the location has been accessed, {@code false} otherwise.
     */
    public boolean alreadyAccessed(Application app, DataLocation loc) {
        LOGGER.debug("Check already accessed: " + loc.getLocationKey());
        String locationKey = loc.getLocationKey();
        Integer fileId = app.getFileDataId(locationKey);
        return fileId != null;
    }

    /**
     * DataInformation interface: returns the last renaming of a given data.
     *
     * @param code Object code.
     * @return Data renaming.
     */
    public String getLastRenaming(int code) {
        Integer aoId = this.codeToId.get(code);
        DataInfo oInfo = this.idToData.get(aoId);
        return oInfo.getCurrentDataVersion().getDataInstanceId().getRenaming();
    }

    /**
     * Returns the original location of a data id.
     *
     * @param fileId File Id.
     * @return Location of the original Data Id.
     */
    public DataLocation getOriginalLocation(int fileId) {
        FileInfo info = (FileInfo) this.idToData.get(fileId);
        return info.getOriginalLocation();
    }

    /**
     * Sets the value {@code value} to the renaming {@code renaming}.
     *
     * @param renaming Renaming.
     * @param value Object value.
     */
    public void setObjectVersionValue(String renaming, Object value) {
        this.valuesOnMain.add(renaming);
        Comm.registerValue(renaming, value);
    }

    /**
     * Returns whether the dataInstanceId is registered in the master or not.
     *
     * @param dId Data Instance Id.
     * @return {@code true} if the renaming is registered in the master, {@code false} otherwise.
     */
    public boolean isHere(DataInstanceId dId) {
        return this.valuesOnMain.contains(dId.getRenaming());
    }

    /**
     * Returns the last data access to a given renaming.
     *
     * @param code Data code.
     * @return Data Instance Id with the last access.
     */
    public DataInstanceId getLastDataAccess(int code) {
        Integer aoId = this.codeToId.get(code);
        DataInfo oInfo = this.idToData.get(aoId);
        return oInfo.getCurrentDataVersion().getDataInstanceId();
    }

    /**
     * Returns the last version of all the specified data Ids {@code dataIds}.
     *
     * @param dataIds Data Ids.
     * @return A list of DataInstaceId containing the last version for each of the specified dataIds.
     */
    public List<DataInstanceId> getLastVersions(TreeSet<Integer> dataIds) {
        List<DataInstanceId> versionIds = new ArrayList<>(dataIds.size());
        for (Integer dataId : dataIds) {
            DataInfo dataInfo = this.idToData.get(dataId);
            if (dataInfo != null) {
                versionIds.add(dataInfo.getCurrentDataVersion().getDataInstanceId());
            } else {
                versionIds.add(null);
            }
        }
        return versionIds;
    }

    /**
     * Unblocks a dataId.
     *
     * @param dataId Data Id.
     */
    public void unblockDataId(Integer dataId) {
        DataInfo dataInfo = this.idToData.get(dataId);
        dataInfo.unblockDeletions();
    }

    /**
     * Waits until data is ready for its safe deletion.
     *
     * @param app Application requesting the data deletion
     * @param loc Data location.
     * @param semWait Waiting semaphore.
     * @return Number of permits.
     */
    public int waitForDataReadyToDelete(Application app, DataLocation loc, Semaphore semWait) {
        LOGGER.debug("Waiting for data to be ready for deletion: " + loc.getPath());
        String locationKey = loc.getLocationKey();

        Integer dataId = app.getFileDataId(locationKey);
        if (dataId == null) {
            LOGGER.debug("No data id found for this data location" + loc.getPath());
            semWait.release();
            return 0;
        }

        DataInfo dataInfo = this.idToData.get(dataId);
        int nPermits = dataInfo.waitForDataReadyToDelete(semWait);
        return nPermits;
    }

    /**
     * Marks a data for deletion.
     *
     * @param data data to be deleted
     * @param noReuse no reuse flag
     * @return DataInfo associated with the data to remove
     */
    public DataInfo deleteData(DataParams data, boolean noReuse) {
        if (DEBUG) {
            LOGGER.debug("Deleting Data associated to " + data.getDescription());
        }
        Integer id = data.removeDataId(this);
        if (id == null) {
            if (DEBUG) {
                LOGGER.debug("No data id found for data associated to " + data.getDescription());
            }
            return null;
        }
        DataInfo dataInfo = this.idToData.get(id);
        if (dataInfo != null) {
            // We delete the data associated with all the versions of the same object
            if (dataInfo.delete(noReuse)) {
                deregisterData(dataInfo);
            }
            return dataInfo;
        } else {
            if (DEBUG) {
                LOGGER.debug("No data found for data associated to " + data.getDescription());
            }
            return null;
        }

    }

    /**
     * Transfers the value of an object.
     *
     * @param toRequest Transfer object request.
     */
    public void transferObjectValue(TransferObjectRequest toRequest) {
        Semaphore sem = toRequest.getSemaphore();
        DataAccessId daId = toRequest.getDaId();
        RWAccessId rwaId = (RWAccessId) daId;
        String sourceName = rwaId.getReadDataInstance().getRenaming();
        // String targetName = rwaId.getWrittenDataInstance().getRenaming();
        if (DEBUG) {
            LOGGER.debug("Requesting getting object " + sourceName);
        }
        LogicalData ld = rwaId.getReadDataInstance().getData();

        if (ld == null) {
            ErrorManager.error("Unregistered data " + sourceName);
            return;
        }

        if (ld.isInMemory()) {
            Object value = null;
            if (!rwaId.isPreserveSourceData()) {
                value = ld.getValue();
                // Clear value
                ld.removeValue();
            } else {
                try {
                    ld.writeToStorage();
                } catch (Exception e) {
                    ErrorManager.error("Exception writing object to file.", e);
                }
                for (DataLocation loc : ld.getLocations()) {
                    if (loc.getProtocol() != ProtocolType.OBJECT_URI) {
                        MultiURI mu = loc.getURIInHost(Comm.getAppHost());
                        String path = mu.getPath();
                        try {
                            value = Serializer.deserialize(path);
                            break;
                        } catch (IOException | ClassNotFoundException e) {
                            ErrorManager.error("Exception writing object to file.", e);
                        }
                    }
                }
            }
            // Set response
            toRequest.setResponse(value);
            toRequest.setTargetData(ld);
            sem.release();
        } else {
            if (DEBUG) {
                LOGGER.debug(
                    "Object " + sourceName + " not in memory. Requesting tranfers to " + Comm.getAppHost().getName());
            }
            DataLocation targetLocation = null;
            String path = ProtocolType.FILE_URI.getSchema() + Comm.getAppHost().getWorkingDirectory() + sourceName;
            try {
                SimpleURI uri = new SimpleURI(path);
                targetLocation = DataLocation.createLocation(Comm.getAppHost(), uri);
            } catch (Exception e) {
                ErrorManager.error(DataLocation.ERROR_INVALID_LOCATION + " " + path, e);
            }
            toRequest.setTargetData(ld);
            Comm.getAppHost().getData(ld, targetLocation, new ObjectTransferable(), new OneOpWithSemListener(sem));
        }

    }

    /**
     * Transfers the value of a binding object.
     *
     * @param toRequest Transfer binding object request.
     * @return Associated LogicalData to the obtained value.
     */
    public LogicalData transferBindingObject(TransferBindingObjectRequest toRequest) {
        DataAccessId daId = toRequest.getDaId();
        RAccessId rwaId = (RAccessId) daId;
        String sourceName = rwaId.getReadDataInstance().getRenaming();

        if (DEBUG) {
            LOGGER.debug("[DataInfoProvider] Requesting getting object " + sourceName);
        }
        LogicalData srcLd = rwaId.getReadDataInstance().getData();
        if (DEBUG) {
            LOGGER.debug("[DataInfoProvider] Logical data for binding object is:" + srcLd);
        }
        if (srcLd == null) {
            ErrorManager.error("Unregistered data " + sourceName);
            return null;
        }
        if (DEBUG) {
            LOGGER.debug("Requesting tranfers binding object " + sourceName + " to " + Comm.getAppHost().getName());
        }

        Semaphore sem = toRequest.getSemaphore();
        BindingObject srcBO = BindingObject.generate(srcLd.getURIs().get(0).getPath());
        BindingObject tgtBO = new BindingObject(sourceName, srcBO.getType(), srcBO.getElements());
        LogicalData tgtLd = srcLd;
        DataLocation targetLocation = new BindingObjectLocation(Comm.getAppHost(), tgtBO);
        Transferable transfer = new BindingObjectTransferable(toRequest);

        Comm.getAppHost().getData(srcLd, targetLocation, tgtLd, transfer, new OneOpWithSemListener(sem));
        if (DEBUG) {
            LOGGER.debug(" Setting tgtName " + transfer.getDataTarget() + " in " + Comm.getAppHost().getName());
        }
        return srcLd;
    }

    /**
     * Blocks dataId and retrieves its result file.
     *
     * @param dataId Data Id.
     * @param listener Result listener.
     * @return The result file.
     */
    public ResultFile blockDataAndGetResultFile(int dataId, ResultListener listener) {
        DataInstanceId lastVersion;
        if (DEBUG) {
            LOGGER.debug("Get Result file for data " + dataId);
        }
        FileInfo fileInfo = (FileInfo) this.idToData.get(dataId);
        if (fileInfo != null) { // FileInfo
            if (fileInfo.hasBeenCanceled()) {
                if (!fileInfo.isCurrentVersionToDelete()) { // If current version is to delete do not
                    // transfer
                    String[] splitPath = fileInfo.getOriginalLocation().getPath().split(File.separator);
                    String origName = splitPath[splitPath.length - 1];
                    if (origName.startsWith("compss-serialized-obj_")) {
                        // Do not transfer objects serialized by the bindings
                        if (DEBUG) {
                            LOGGER.debug("Discarding file " + origName + " as a result");
                        }
                        return null;
                    }
                    fileInfo.blockDeletions();

                    lastVersion = fileInfo.getCurrentDataVersion().getDataInstanceId();

                    ResultFile rf = new ResultFile(lastVersion, fileInfo.getOriginalLocation());

                    DataInstanceId fId = rf.getFileInstanceId();
                    String renaming = fId.getRenaming();

                    // Look for the last available version
                    while (renaming != null && !Comm.existsData(renaming)) {
                        renaming = DataInstanceId.previousVersionRenaming(renaming);
                    }
                    if (renaming == null) {
                        LOGGER.error(RES_FILE_TRANSFER_ERR + ": Cannot transfer file " + fId.getRenaming()
                            + " nor any of its previous versions");
                        return null;
                    }

                    LogicalData data = Comm.getData(renaming);
                    // Check if data is a PSCO and must be consolidated
                    for (DataLocation loc : data.getLocations()) {
                        if (loc instanceof PersistentLocation) {
                            String pscoId = ((PersistentLocation) loc).getId();
                            if (Tracer.isActivated()) {
                                Tracer.emitEvent(TraceEvent.STORAGE_CONSOLIDATE);
                            }
                            try {
                                StorageItf.consolidateVersion(pscoId);
                            } catch (StorageException e) {
                                LOGGER.error("Cannot consolidate PSCO " + pscoId, e);
                            } finally {
                                if (Tracer.isActivated()) {
                                    Tracer.emitEventEnd(TraceEvent.STORAGE_CONSOLIDATE);
                                }
                            }
                            LOGGER.debug("Returned because persistent object");
                            return rf;
                        }

                    }

                    // If no PSCO location is found, perform normal getData
                    if (rf.getOriginalLocation().getProtocol() == ProtocolType.BINDING_URI) {
                        // Comm.getAppHost().getData(data, rf.getOriginalLocation(), new
                        // BindingObjectTransferable(),
                        // listener);
                        if (DEBUG) {
                            LOGGER.debug("Discarding data d" + dataId + " as a result beacuse it is a binding object");
                        }
                    } else {
                        if (rf.getOriginalLocation().getProtocol() == ProtocolType.DIR_URI) {
                            listener.addOperation();
                            Comm.getAppHost().getData(data, rf.getOriginalLocation(), new DirectoryTransferable(),
                                listener);
                        } else {
                            listener.addOperation();
                            Comm.getAppHost().getData(data, rf.getOriginalLocation(), new FileTransferable(), listener);
                        }
                    }

                    return rf;
                } else {
                    if (fileInfo.isCurrentVersionToDelete()) {
                        if (DEBUG) {
                            String[] splitPath = fileInfo.getOriginalLocation().getPath().split(File.separator);
                            String origName = splitPath[splitPath.length - 1];
                            LOGGER.debug("Trying to delete file " + origName);
                        }
                        if (fileInfo.delete(true)) {
                            deregisterData(fileInfo);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Shuts down the component.
     */
    public void shutdown() {
        // Nothing to do
    }

    /**
     * Removes all data bound to the specified application.
     *
     * @param app application whose that must be removed from the system
     */
    public void removeAllApplicationData(Application app) {
        List<DataInfo> data = app.popAllData();
        for (DataInfo di : data) {
            di.delete(true);
        }
    }

}
