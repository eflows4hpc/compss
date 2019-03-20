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
package es.bsc.compss.scheduler.types.fake;

import es.bsc.es.bsc.compss.scheduler.fullGraphScheduler.FullGraphResourceScheduler;
import es.bsc.es.bsc.compss.scheduler.types.ActionOrchestrator;
import es.bsc.compss.types.resources.Worker;


public class FakeResourceScheduler extends FullGraphResourceScheduler<FakeProfile, FakeResourceDescription, FakeImplementation> {

    private long fakeLastGapStart;


    public FakeResourceScheduler(Worker<FakeResourceDescription, FakeImplementation> w,
            ActionOrchestrator<FakeProfile, FakeResourceDescription, FakeImplementation> orchestrator, long fakeLastGapStart) {

        super(w, orchestrator);
        this.fakeLastGapStart = fakeLastGapStart;
    }

    @Override
    public FakeProfile generateProfileForAllocatable() {
        return new FakeProfile(0);
    }

    @Override
    public long getLastGapExpectedStart() {
        return fakeLastGapStart;
    }

}
