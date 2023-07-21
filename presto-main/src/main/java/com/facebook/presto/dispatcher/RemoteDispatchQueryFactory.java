package com.facebook.presto.dispatcher;

import com.facebook.presto.Session;
import com.facebook.presto.common.analyzer.PreparedQuery;
import com.facebook.presto.common.resourceGroups.QueryType;
import com.facebook.presto.execution.LocationFactory;
import com.facebook.presto.execution.QueryStateMachine;
import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.spi.WarningCollector;
import com.facebook.presto.spi.analyzer.AnalyzerProvider;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.facebook.presto.spi.security.AccessControl;
import com.facebook.presto.transaction.TransactionManager;
import com.google.common.util.concurrent.ListeningExecutorService;

import javax.inject.Inject;

import java.util.Optional;
import java.util.function.Consumer;

public class RemoteDispatchQueryFactory
        implements DispatchQueryFactory
{
    private final LocationFactory locationFactory;
    private final TransactionManager transactionManager;
    private final AccessControl accessControl;
    private final ListeningExecutorService executor;
    private final Metadata metadata;
    private final RemoteDispatchTracker dispatchTracker;

    @Inject
    public RemoteDispatchQueryFactory(
            LocationFactory locationFactory,
            TransactionManager transactionManager,
            AccessControl accessControl,
            DispatchExecutor dispatchExecutor,
            Metadata metadata,
            RemoteDispatchTracker dispatchTracker)
    {
        this.locationFactory = locationFactory;
        this.transactionManager = transactionManager;
        this.accessControl = accessControl;
        this.executor = dispatchExecutor.getExecutor();
        this.metadata = metadata;
        this.dispatchTracker = dispatchTracker;
    }

    @Override
    public DispatchQuery createDispatchQuery(Session session, AnalyzerProvider analyzerProvider, String query, PreparedQuery preparedQuery, String slug, int retryCount, ResourceGroupId resourceGroup, Optional<QueryType> queryType, WarningCollector warningCollector, Consumer<DispatchQuery> queryQueuer)
    {
        QueryStateMachine stateMachine = QueryStateMachine.begin(
                query,
                preparedQuery.getPrepareSql(),
                session,
                locationFactory.createQueryLocation(session.getQueryId()),
                resourceGroup,
                queryType,
                preparedQuery.isTransactionControlStatement(),
                transactionManager,
                accessControl,
                executor,
                metadata,
                warningCollector);
        return new RemoteDispatchQuery(stateMachine, queryQueuer, dispatchTracker::registerQuery);
    }
}
