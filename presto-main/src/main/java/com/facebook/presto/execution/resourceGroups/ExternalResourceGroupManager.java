package com.facebook.presto.execution.resourceGroups;

import com.facebook.airlift.log.Logger;
import com.facebook.presto.execution.ManagedQueryExecution;
import com.facebook.presto.server.BasicQueryInfo;
import com.facebook.presto.server.ResourceGroupInfo;
import com.facebook.presto.spi.resourceGroups.ResourceGroupConfigurationManagerFactory;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.facebook.presto.spi.resourceGroups.SelectionContext;
import com.facebook.presto.spi.resourceGroups.SelectionCriteria;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executor;

public class ExternalResourceGroupManager<C>
        implements ResourceGroupManager<C>
{
    private static final Logger log = Logger.get(ExternalResourceGroupManager.class);
    private static final FileQueue dispatchQueue = new FileQueue(Paths.get("/Users/prithvip/Work/regional-queueing/dispatch-queue"));
    private static final FileQueue pollQueue = new FileQueue(Paths.get("/Users/prithvip/Work/regional-queueing/poll-queue"));

    @Override
    public void submit(ManagedQueryExecution queryExecution, SelectionContext<C> selectionContext, Executor executor)
    {
        // TODO: Figure out how to not lose a bunch of work here with parsing/analysis/context selection, etc...
        BasicQueryInfo queryInfo = queryExecution.getBasicQueryInfo();
        dispatchQueue.add(queryInfo);
        executor.execute(queryExecution::startWaitingForResources);
    }

    public BasicQueryInfo poll()
    {
        return pollQueue.poll();
    }

    @Override
    public SelectionContext<C> selectGroup(SelectionCriteria criteria)
    {
        // TODO: Either we can 1) fetch selection context from external resource or 2) inline this into submit method
        return new SelectionContext<>(new ResourceGroupId("root"), null);
    }

    @Override
    public ResourceGroupInfo getResourceGroupInfo(ResourceGroupId id, boolean includeQueryInfo, boolean summarizeSubgroups, boolean includeStaticSubgroupsOnly)
    {
        return null;
    }

    @Override
    public List<ResourceGroupInfo> getPathToRoot(ResourceGroupId id)
    {
        return null;
    }

    @Override
    public void addConfigurationManagerFactory(ResourceGroupConfigurationManagerFactory factory)
    {

    }

    @Override
    public void loadConfigurationManager()
            throws Exception
    {

    }

    @Override
    public List<ResourceGroupRuntimeInfo> getResourceGroupRuntimeInfos()
    {
        return null;
    }


}
