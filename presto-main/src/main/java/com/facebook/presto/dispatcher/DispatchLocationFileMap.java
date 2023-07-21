package com.facebook.presto.dispatcher;

import com.facebook.airlift.json.JsonObjectMapperProvider;
import com.facebook.airlift.log.Logger;
import com.facebook.presto.spi.QueryId;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DispatchLocationFileMap
{
    private static final Logger log = Logger.get(RemoteDispatchTracker.class);
    private static final ObjectMapper OBJECT_MAPPER = new JsonObjectMapperProvider().get();
    private static final Path DISPATCH_INFO_DIRECTORY = Paths.get("/Users/prithvip/Work/regional-queueing/dispatch-info");

    public Map<QueryId, RemoteCoordinatorLocation> getDispatchLocations()
    {
        try {
            File[] dispatchLocationFiles = DISPATCH_INFO_DIRECTORY.toFile().listFiles();
            if (dispatchLocationFiles == null) {
                return new HashMap<>();
            }
            Map<QueryId, RemoteCoordinatorLocation> dispatchLocations = new HashMap<>();
            for (File file: dispatchLocationFiles) {
                dispatchLocations.put(new QueryId(file.getName()), OBJECT_MAPPER.readValue(file, RemoteCoordinatorLocation.class));
            }
            return dispatchLocations;
        }
        catch (IOException e) {
            log.error(e);
            return new HashMap<>();
        }
    }

    public void add(QueryId queryId, CoordinatorLocation coordinatorLocation)
    {
        try {
            OBJECT_MAPPER.writeValue(DISPATCH_INFO_DIRECTORY.resolve(queryId.getId()).toFile(), coordinatorLocation);
        }
        catch (IOException e) {
            log.error(e);
        }
    }
}
