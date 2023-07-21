package com.facebook.presto.dispatcher;

import com.facebook.presto.spi.QueryId;

// TODO: Need to consolidate the external vs internal paths somehow so this is cleaner... That way we support both external durable queue
//   and an in-memory RM based queue manager which triggers on a coordinator poll. In its simplest form, this is just a callback.
public interface DispatchTracker
{
    /**
     * Transition query to dispatched state once it has been dispatched to a coordinator
     */
    void registerQuery(DispatchQuery dispatchQuery);

    /**
     * Stop tracking the dispatch of this query
     */
    void expireQuery(QueryId queryId);

    /**
     * Acknowledge that query has been received, started, and we are now ready for clients to poll ExecutingStatementResource
     */
    void acknowledgeDispatch(QueryId queryId, CoordinatorLocation coordinatorLocation);
}
