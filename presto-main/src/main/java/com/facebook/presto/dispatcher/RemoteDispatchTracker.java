package com.facebook.presto.dispatcher;

import com.facebook.airlift.log.Logger;
import com.facebook.presto.spi.QueryId;
import com.facebook.presto.util.PeriodicTaskExecutor;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RemoteDispatchTracker
        implements DispatchTracker
{
    private static final Logger log = Logger.get(RemoteDispatchTracker.class);

    private final DispatchLocationFileMap dispatchLocationProvider;
    private final ConcurrentMap<QueryId, DispatchQuery> dispatchingQueries = new ConcurrentHashMap<>();
    private final PeriodicTaskExecutor pollingExecutor;

    @Inject
    public RemoteDispatchTracker(DispatchExecutor dispatchExecutor, DispatchLocationFileMap dispatchLocationProvider)
    {
        this.pollingExecutor = new PeriodicTaskExecutor(1000, dispatchExecutor.getScheduledExecutor(), this::pollAndDispatch);
        this.dispatchLocationProvider = dispatchLocationProvider;
    }

    @PostConstruct
    public void start()
    {
        pollingExecutor.start();
    }

    private void pollAndDispatch()
    {
        Map<QueryId, RemoteCoordinatorLocation> dispatchLocations = dispatchLocationProvider.getDispatchLocations();
        for (DispatchQuery dq : dispatchingQueries.values()) {
            if (dispatchLocations.containsKey(dq.getQueryId())) {
                dq.dispatch(dispatchLocations.get(dq.getQueryId()));
                dispatchingQueries.remove(dq.getQueryId());
            }
        }
    }

    @Override
    public void registerQuery(DispatchQuery dispatchQuery)
    {
        dispatchingQueries.put(dispatchQuery.getQueryId(), dispatchQuery);
    }

    @Override
    public void expireQuery(QueryId queryId)
    {
        // TODO: Fire this on a state change to query cancelled or query failed
        dispatchingQueries.remove(queryId);
    }

    @Override
    public void acknowledgeDispatch(QueryId queryId, CoordinatorLocation coordinatorLocation)
    {
        dispatchLocationProvider.add(queryId, coordinatorLocation);
    }
}
