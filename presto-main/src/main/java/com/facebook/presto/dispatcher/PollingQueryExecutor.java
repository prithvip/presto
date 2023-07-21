package com.facebook.presto.dispatcher;

import com.facebook.airlift.log.Logger;
import com.facebook.presto.Session;
import com.facebook.presto.common.analyzer.PreparedQuery;
import com.facebook.presto.execution.ExecutionFactoriesManager;
import com.facebook.presto.execution.LocationFactory;
import com.facebook.presto.execution.QueryExecution;
import com.facebook.presto.execution.QueryExecution.QueryExecutionFactory;
import com.facebook.presto.execution.QueryManager;
import com.facebook.presto.execution.QueryStateMachine;
import com.facebook.presto.execution.resourceGroups.ExternalResourceGroupManager;
import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.metadata.SessionPropertyManager;
import com.facebook.presto.server.BasicQueryInfo;
import com.facebook.presto.spi.analyzer.AnalyzerOptions;
import com.facebook.presto.spi.analyzer.AnalyzerProvider;
import com.facebook.presto.sql.analyzer.AnalyzerProviderManager;
import com.facebook.presto.transaction.TransactionManager;
import com.facebook.presto.util.PeriodicTaskExecutor;
import com.google.common.util.concurrent.ListeningExecutorService;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import java.util.concurrent.ScheduledExecutorService;

import static com.facebook.presto.SystemSessionProperties.getAnalyzerType;
import static com.facebook.presto.util.AnalyzerUtil.createAnalyzerOptions;
import static java.util.concurrent.Executors.newScheduledThreadPool;

public class PollingQueryExecutor
{
    private static final Logger log = Logger.get(PollingQueryExecutor.class);

    private final ScheduledExecutorService scheduledExecutorService = newScheduledThreadPool(1);
    private final PeriodicTaskExecutor pollingExecutor;
    private final ExternalResourceGroupManager externalResourceGroupManager;
    private final SessionPropertyManager sessionPropertyManager;
    private final AnalyzerProviderManager analyzerProviderManager;
    private final ExecutionFactoriesManager executionFactoriesManager;
    private final LocationFactory locationFactory;
    private final TransactionManager transactionManager;
    private final ListeningExecutorService executor;
    private final Metadata metadata;
    private final QueryManager queryManager;
    private final RemoteDispatchTracker dispatchTracker;

    @Inject
    public PollingQueryExecutor(
            ExternalResourceGroupManager externalResourceGroupManager,
            SessionPropertyManager sessionPropertyManager,
            AnalyzerProviderManager analyzerProviderManager,
            ExecutionFactoriesManager executionFactoriesManager,
            LocationFactory locationFactory,
            TransactionManager transactionManager,
            DispatchExecutor executor,
            Metadata metadata,
            QueryManager queryManager,
            RemoteDispatchTracker dispatchTracker)
    {
        this.externalResourceGroupManager = externalResourceGroupManager;
        this.sessionPropertyManager = sessionPropertyManager;
        this.analyzerProviderManager = analyzerProviderManager;
        this.executionFactoriesManager = executionFactoriesManager;
        this.locationFactory = locationFactory;
        this.transactionManager = transactionManager;
        this.executor = executor.getExecutor();
        this.metadata = metadata;
        this.queryManager = queryManager;
        this.dispatchTracker = dispatchTracker;
        // TODO: Make this configurable
        this.pollingExecutor = new PeriodicTaskExecutor(1000, scheduledExecutorService, this::pollAndExecute);
    }

    @PostConstruct
    public void start()
    {
        pollingExecutor.start();
    }

    // TODO: Need to come back to this and refactor to share this logic with DispatchManager
    // TODO: How to handle transactions?
    private void pollAndExecute()
    {
        // TODO: Need a synchronization mechanism here so query is registered before client polls
        BasicQueryInfo queryInfo = externalResourceGroupManager.poll();

        // TODO: Check permissions and authorize identity

        // Create session
        // TODO: Handle extra credentials and extra authenticators
        // TODO: How to handle session defaults and overrides? Need to think about if that should be done
        //   per-coordinator or let dispatcher set it. These properties can also be set per-resource group.
        Session session = queryInfo.getSession().toSession(sessionPropertyManager);

        // Query preparation and analysis
        AnalyzerOptions analyzerOptions = createAnalyzerOptions(session, session.getWarningCollector());
        AnalyzerProvider analyzerProvider = analyzerProviderManager.getAnalyzerProvider(getAnalyzerType(session));
        String query = queryInfo.getQuery();
        // TODO: Dispatcher should handle query preparation at the SQL level
        //   We need that to support PREPARE, DEALLOCATE, and EXECUTE SQL statements.
        //   This is also necessary (but not sufficient) to support temporary (session-scoped) functions, which is a whole other headache to come back to...
        PreparedQuery preparedQuery = analyzerProvider.getQueryPreparer().prepareQuery(analyzerOptions, query, session.getPreparedStatements(), session.getWarningCollector());
        query = preparedQuery.getFormattedQuery().orElse(query);

        // Create and bootstrap the state machine
        // TODO: Right now we are just re-using dispatch executor for convenience, but we should replace this with a dedicated state change event executor
        QueryStateMachine stateMachine = QueryStateMachine.beginDispatchedQuery(
                query,
                preparedQuery.getPrepareSql(),
                session,
                locationFactory.createQueryLocation(queryInfo.getQueryId()),
                queryInfo.getResourceGroupId().get(),
                preparedQuery.getQueryType(),
                transactionManager,
                executor,
                metadata,
                session.getWarningCollector());

        // TODO: Register this query with the QueryStateTracingListener
        // TODO: Send some kind of "received" event to event listener so its in our logs

        try {
            // Create query execution
            QueryExecutionFactory<?> queryExecutionFactory = executionFactoriesManager.getExecutionFactory(preparedQuery);
            // TODO: Submit this to another executor to decouple query execution from polling; return this as a future
            QueryExecution queryExecution = queryExecutionFactory.createQueryExecution(
                    analyzerProvider,
                    preparedQuery, stateMachine,
                    // TODO: Need to serialize slug into the external queue representation, so we can check it later
                    "slug",
                    // TODO: Support transparent retry functionality
                    0,
                    session.getWarningCollector(),
                    preparedQuery.getQueryType());

            // TODO: Set per-resource group execution limits before beginning execution; probably these should get serialized into the queue representation of the query
            queryManager.createQuery(queryExecution);

            // Send ack that query has been received and started, because we are ready for clients to poll us now. After this ack, the queue shouldn't attempt redelivery.
            // TODO: Don't hardcode location
            dispatchTracker.acknowledgeDispatch(queryInfo.getQueryId(), new RemoteCoordinatorLocation("localhost", Integer.parseInt(System.getProperty("listening-port"))));
        } catch (Exception e) {
            log.error(e);
        }
    }


}
