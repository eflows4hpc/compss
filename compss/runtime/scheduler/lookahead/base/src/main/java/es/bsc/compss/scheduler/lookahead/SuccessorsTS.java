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
package es.bsc.compss.scheduler.lookahead;

import es.bsc.compss.components.impl.ResourceScheduler;
import es.bsc.compss.scheduler.exceptions.BlockedActionException;
import es.bsc.compss.scheduler.exceptions.UnassignedActionException;
import es.bsc.compss.scheduler.types.AllocatableAction;
import es.bsc.compss.scheduler.types.ObjectValue;
import es.bsc.compss.scheduler.types.Score;
import es.bsc.compss.types.resources.WorkerResourceDescription;
import es.bsc.compss.util.ActionSet;

import java.util.List;
import java.util.PriorityQueue;


/**
 * Representation of a Scheduler that considers only ready tasks and sorts them in FIFO mode + data locality.
 */
public abstract class SuccessorsTS extends LookaheadTS {

    /**
     * Constructs a new FIFODataScheduler instance.
     */
    public SuccessorsTS() {
        super();
    }

    /*
     * *********************************************************************************************************
     * *********************************************************************************************************
     * ********************************* SCHEDULING OPERATIONS *************************************************
     * *********************************************************************************************************
     * *********************************************************************************************************
     */
    @Override
    public <T extends WorkerResourceDescription> PriorityQueue<ObjectValue<AllocatableAction>> getCandidateFreeActions(
        List<AllocatableAction> dataFreeActions, List<AllocatableAction> resourceFreeActions,
        ActionSet unassignedActions, List<AllocatableAction> blockedCandidates, ResourceScheduler<T> resource) {

        LOGGER.debug("[DataScheduler] Treating dependency free actions");

        PriorityQueue<ObjectValue<AllocatableAction>> executableActions = new PriorityQueue<>();

        for (AllocatableAction action : dataFreeActions) {
            Score actionScore = this.generateActionScore(action);
            Score fullScore = action.schedulingScore(resource, actionScore);
            ObjectValue<AllocatableAction> obj = new ObjectValue<>(action, fullScore);
            executableActions.add(obj);
        }
        dataFreeActions.clear();
        while (!executableActions.isEmpty()) {
            ObjectValue<AllocatableAction> obj = executableActions.poll();
            AllocatableAction freeAction = obj.getObject();
            try {
                scheduleAction(freeAction, resource, obj.getScore());
                tryToLaunch(freeAction);
            } catch (BlockedActionException e) {
                blockedCandidates.add(freeAction);
            } catch (UnassignedActionException e) {
                dataFreeActions.add(freeAction);
            }
        }

        return super.getCandidateFreeActions(dataFreeActions, resourceFreeActions, unassignedActions, blockedCandidates,
            resource);
    }

}
