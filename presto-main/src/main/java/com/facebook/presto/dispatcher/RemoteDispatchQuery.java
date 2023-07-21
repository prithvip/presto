package com.facebook.presto.dispatcher;

import com.facebook.presto.Session;
import com.facebook.presto.common.ErrorCode;
import com.facebook.presto.execution.ExecutionFailureInfo;
import com.facebook.presto.execution.QueryState;
import com.facebook.presto.execution.QueryStateMachine;
import com.facebook.presto.execution.StateMachine;
import com.facebook.presto.server.BasicQueryInfo;
import com.facebook.presto.spi.QueryId;
import com.facebook.presto.spi.resourceGroups.ResourceGroupQueryLimits;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.joda.time.DateTime;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.nonCancellationPropagating;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Objects.requireNonNull;

public class RemoteDispatchQuery
    implements DispatchQuery
{
    private static final Duration NO_DURATION = new Duration(0, TimeUnit.MILLISECONDS);

    private final QueryStateMachine stateMachine;
    private final Consumer<DispatchQuery> queryQueuer;
    private final Consumer<DispatchQuery> dispatchTracker;

    // TODO: Use QueryMonitor to log queryCreated and queryFailure events to eventListener
    public RemoteDispatchQuery(QueryStateMachine stateMachine, Consumer<DispatchQuery> queryQueuer, Consumer<DispatchQuery> dispatchTracker)
    {
        this.stateMachine = requireNonNull(stateMachine, "stateMachine is null");
        this.queryQueuer = requireNonNull(queryQueuer, "queryQueuer is null");
        this.dispatchTracker = requireNonNull(dispatchTracker, "dispatchTracker is null");
    }

    // TODO: Implement rest of DispatchQuery interface

    @Override
    public void recordHeartbeat()
    {
        // TODO: Heartbeat should get sent to ExternalResourceGroupManager so query gets expired if dispatcher dies
        stateMachine.recordHeartbeat();
    }

    @Override
    public DateTime getLastHeartbeat()
    {
        // TODO: Need to track this from QueryTracker
        return stateMachine.getLastHeartbeat();
    }

    @Override
    public ListenableFuture<?> getDispatchedFuture()
    {
        return queryDispatchFuture(stateMachine.getQueryState());
    }

    private ListenableFuture<?> queryDispatchFuture(QueryState currentState)
    {
        // TODO: QueuedStatementResource needs to consume this for the redirect fast-path. Do we want to support that?
        //   If so, it's probably cleaner to have this future as a class member instead, and then set it during a state change
        //   using the state machine listener.
        if (currentState.ordinal() >= QueryState.DISPATCHED.ordinal()) {
            return immediateFuture(null);
        }
        return Futures.transformAsync(stateMachine.getStateChange(currentState), this::queryDispatchFuture, directExecutor());
    }

    @Override
    public DispatchInfo getDispatchInfo()
    {
        // TODO: Track and fetch real duration info from the state machine.
        QueryState currentState = stateMachine.getQueryState();
        if (currentState == QueryState.WAITING_FOR_PREREQUISITES) {
            return DispatchInfo.waitingForPrerequisites(NO_DURATION, NO_DURATION);
        } else if (currentState == QueryState.QUEUED || currentState == QueryState.WAITING_FOR_RESOURCES || currentState == QueryState.DISPATCHING) {
            return DispatchInfo.queued(NO_DURATION, NO_DURATION, NO_DURATION);
        } else if (currentState == QueryState.DISPATCHED) {
            // TODO: Handle error case if state is dispatched but dispatched location isn't present
            return DispatchInfo.dispatched(stateMachine.getDispatchedLocation().get(), NO_DURATION, NO_DURATION, NO_DURATION);
        } else if (currentState == QueryState.FAILED) {
            // TODO: Return proper failure info back to client instead of swallowing here, so we need to thread that through state machine.
            return DispatchInfo.failed(null, NO_DURATION, NO_DURATION, NO_DURATION);
        }
        // TODO: But this shouldn't happen?
        return null;
    }

    @Override
    public void cancel()
    {
        // TODO: Send proper exception and log queryCancel event. Also need to add logic here to extend cancellation to ExternalResourceGroupManager.
        stateMachine.transitionToCanceled();
    }

    @Override
    public void startWaitingForPrerequisites()
    {
        // TODO: Implement waiting for prerequisites check before the query gets queued
        queueQuery();
    }

    private void queueQuery()
    {
        if (stateMachine.transitionToQueued()) {
            queryQueuer.accept(this);
        }
    }

    @Override
    public void startWaitingForResources()
    {
        // TODO: Clean this up, because in standalone dispatcher world, we don't need waiting for resources state
        stateMachine.transitionToWaitingForResources();
        stateMachine.transitionToDispatching();
        // Start polling from DispatchTracker to see where query got dispatched to
        dispatchTracker.accept(this);
    }

    @Override
    public void dispatch(CoordinatorLocation coordinatorLocation)
    {
        stateMachine.transitionToDispatched(coordinatorLocation);
    }

    @Override
    public void addStateChangeListener(StateMachine.StateChangeListener<QueryState> stateChangeListener)
    {

    }

    @Override
    public DataSize getUserMemoryReservation()
    {
        return new DataSize(0, DataSize.Unit.BYTE);
    }

    @Override
    public DataSize getTotalMemoryReservation()
    {
        return new DataSize(0, DataSize.Unit.BYTE);
    }

    @Override
    public Duration getTotalCpuTime()
    {
        return new Duration(0, TimeUnit.MILLISECONDS);
    }

    @Override
    public BasicQueryInfo getBasicQueryInfo()
    {
        return stateMachine.getBasicQueryInfo(Optional.empty());
    }

    @Override
    public void setResourceGroupQueryLimits(ResourceGroupQueryLimits resourceGroupQueryLimits)
    {

    }

    @Override
    public Optional<ErrorCode> getErrorCode()
    {
        return Optional.empty();
    }

    @Override
    public boolean isRetry()
    {
        return false;
    }

    @Override
    public QueryId getQueryId()
    {
        return stateMachine.getQueryId();
    }

    @Override
    public boolean isDone()
    {
        return stateMachine.getQueryState().isDone();
    }

    @Override
    public Session getSession()
    {
        return stateMachine.getSession();
    }

    @Override
    public DateTime getCreateTime()
    {
        return stateMachine.getCreateTime();
    }

    @Override
    public Optional<DateTime> getExecutionStartTime()
    {
        return stateMachine.getExecutionStartTime();
    }

    @Override
    public Optional<DateTime> getEndTime()
    {
        return stateMachine.getEndTime();
    }

    @Override
    public Optional<ResourceGroupQueryLimits> getResourceGroupQueryLimits()
    {
        return Optional.empty();
    }

    @Override
    public void fail(Throwable cause)
    {

    }

    @Override
    public void pruneInfo()
    {

    }
}
