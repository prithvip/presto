package com.facebook.presto.execution.resourceGroups;

import com.facebook.airlift.json.JsonObjectMapperProvider;
import com.facebook.airlift.log.Logger;
import com.facebook.presto.server.BasicQueryInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Hacky file based queue to prove out the external queueing concept
 */
public class FileQueue
    implements Queue<BasicQueryInfo>
{
    private static final Logger log = Logger.get(FileQueue.class);
    private static final ObjectMapper OBJECT_MAPPER = new JsonObjectMapperProvider().get();

    private final Path directory;

    public FileQueue(Path directory)
    {
        this.directory = directory;
    }

    public void add(BasicQueryInfo query)
    {
        try {
            OBJECT_MAPPER.writeValue(directory.resolve(query.getQueryId().getId()).toFile(), query);
        }
        catch (IOException e) {
            log.error(e);
        }
    }

    @Override
    public boolean contains(BasicQueryInfo query)
    {
        return Files.exists(directory.resolve(query.getQueryId().getId()));
    }

    @Override
    public boolean remove(BasicQueryInfo query)
    {
        try {
            Files.delete(directory.resolve(query.getQueryId().getId()));
            return true;
        }
        catch (IOException e) {
            log.error(e);
            return false;
        }
    }

    @Override
    public BasicQueryInfo poll()
    {
        BasicQueryInfo query = peek();
        try {
            Files.delete(directory.resolve(query.getQueryId().getId()));
            return query;
        }
        catch (IOException e) {
            log.error(e);
            return null;
        }
    }

    @Override
    public BasicQueryInfo peek()
    {
        File[] queryFiles = directory.toFile().listFiles();
        if (queryFiles.length == 0) {
            return null;
        }
        Arrays.sort(queryFiles, Comparator.comparingLong(File::lastModified));
        try {
            return OBJECT_MAPPER.readValue(queryFiles[0], BasicQueryInfo.class);
        }
        catch (IOException e) {
            log.error(e);
            return null;
        }
    }

    @Override
    public int size()
    {
        return directory.toFile().listFiles().length;
    }

    @Override
    public boolean isEmpty()
    {
        return size() == 0;
    }
}
