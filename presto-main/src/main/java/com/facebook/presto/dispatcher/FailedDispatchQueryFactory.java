package com.facebook.presto.dispatcher;

import com.facebook.presto.Session;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;

import java.util.Optional;

public interface FailedDispatchQueryFactory
{
    FailedDispatchQuery createFailedDispatchQuery(Session session, String query, Optional<ResourceGroupId> resourceGroup, Throwable throwable);
}
