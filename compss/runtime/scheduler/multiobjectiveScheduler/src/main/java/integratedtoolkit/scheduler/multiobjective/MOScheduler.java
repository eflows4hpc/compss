package integratedtoolkit.scheduler.multiobjective;

import integratedtoolkit.components.impl.ResourceScheduler;
import integratedtoolkit.components.impl.TaskScheduler;
import integratedtoolkit.scheduler.multiobjective.types.MOProfile;
import integratedtoolkit.scheduler.multiobjective.types.MOScore;
import integratedtoolkit.scheduler.types.AllocatableAction;
import integratedtoolkit.scheduler.types.Score;
import integratedtoolkit.types.CloudProvider;
import integratedtoolkit.types.implementations.Implementation;
import integratedtoolkit.types.resources.Worker;
import integratedtoolkit.types.resources.WorkerResourceDescription;
import integratedtoolkit.types.resources.description.CloudInstanceTypeDescription;
import integratedtoolkit.util.CoreManager;
import integratedtoolkit.util.ResourceOptimizer;
import integratedtoolkit.util.SchedulingOptimizer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;

public class MOScheduler extends TaskScheduler {

    private final MOScore dummyScore = new MOScore(0, 0, 0, 0, 0, 0);

    public MOScheduler() {
    }

    @Override
    public MOProfile generateProfile(JSONObject json) {
        return new MOProfile(json);
    }

    @Override
    public <T extends WorkerResourceDescription> MOResourceScheduler<T> generateSchedulerForResource(Worker<T> w, JSONObject res, JSONObject impls) {
        // LOGGER.debug("[LoadBalancingScheduler] Generate scheduler for resource " + w.getName());
        return new MOResourceScheduler<>(w, res, impls);
    }

    @Override
    public <T extends WorkerResourceDescription> MOSchedulingInformation generateSchedulingInformation(
            ResourceScheduler<T> enforcedTargetResource) {
        return new MOSchedulingInformation(enforcedTargetResource);
    }

    @Override
    public Score generateActionScore(AllocatableAction action) {
        long actionScore = MOScore.getActionScore(action);
        long dataTime = dummyScore.getDataPredecessorTime(action.getDataPredecessors());
        return new MOScore(actionScore, dataTime, 0, 0, 0, 0);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Collection<ResourceScheduler<? extends WorkerResourceDescription>> workers = this.getWorkers();
        System.out.println("End Profiles:");
        for (ResourceScheduler<?> worker : workers) {
            System.out.println("\t" + worker.getName());
            for (int coreId = 0; coreId < CoreManager.getCoreCount(); coreId++) {
                for (Implementation impl : CoreManager.getCoreImplementations(coreId)) {
                    System.out.println("\t\t" + CoreManager.getSignature(coreId, impl.getImplementationId()));
                    MOProfile profile = (MOProfile) worker.getProfile(impl);
                    System.out.println("\t\t\tTime " + profile.getAverageExecutionTime() + " ms");
                    System.out.println("\t\t\tPower " + profile.getPower() + " W");
                    System.out.println("\t\t\tCost " + profile.getPrice() + " €");
                }
            }
        }
    }

    @Override
    public ResourceOptimizer generateResourceOptimizer() {
        return new MOResourceOptimizer(this);
    }

    @Override
    public SchedulingOptimizer generateSchedulingOptimizer() {
        return new MOScheduleOptimizer(this);
    }

    /**
     * Notifies to the scheduler that some actions have become free of data
     * dependencies or resource dependencies.
     *
     * @param <T>
     * @param dataFreeActions IN, list of actions free of data dependencies
     * @param resourceFreeActions IN, list of actions free of resource
     * dependencies
     * @param blockedCandidates OUT, list of blocked candidates
     * @param resource Resource where the previous task was executed
     */
    @Override
    public <T extends WorkerResourceDescription> void handleDependencyFreeActions(List<AllocatableAction> dataFreeActions,
            List<AllocatableAction> resourceFreeActions, List<AllocatableAction> blockedCandidates, ResourceScheduler<T> resource) {

        Set<AllocatableAction> freeTasks = new HashSet<>();
        freeTasks.addAll(dataFreeActions);
        freeTasks.addAll(resourceFreeActions);
        for (AllocatableAction freeAction : freeTasks) {
            tryToLaunch(freeAction);
        }
    }

    public JSONObject getJSONForCloudInstanceTypeDescription(CloudProvider cp, CloudInstanceTypeDescription ctid) {
        return jsm.getJSONForCloudInstanceTypeDescription(cp, ctid);
    }

    public MOProfile getDefaultProfile(CloudProvider cp, CloudInstanceTypeDescription ctid, int coreId, int implId) {
        return generateProfile(jsm.getJSONForImplementation(cp, ctid, coreId, implId));
    }
}
