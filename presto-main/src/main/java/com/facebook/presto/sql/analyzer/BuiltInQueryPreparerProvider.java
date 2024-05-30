package com.facebook.presto.sql.analyzer;

import com.facebook.presto.spi.analyzer.QueryPreparer;
import com.facebook.presto.spi.analyzer.QueryPreparerProvider;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class BuiltInQueryPreparerProvider
        implements QueryPreparerProvider
{
    private static final String PROVIDER_NAME = "BUILTIN";
    private final BuiltInQueryPreparer queryPreparer;

    @Inject
    public BuiltInQueryPreparerProvider(BuiltInQueryPreparer queryPreparer)
    {
        this.queryPreparer = requireNonNull(queryPreparer, "queryPreparer is null");
    }

    @Override
    public String getType()
    {
        return PROVIDER_NAME;
    }

    @Override
    public QueryPreparer getQueryPreparer()
    {
        return queryPreparer;
    }
}
