package com.facebook.presto.spi.analyzer;

public interface QueryPreparerProvider
{
    String getType();

    QueryPreparer getQueryPreparer();
}
