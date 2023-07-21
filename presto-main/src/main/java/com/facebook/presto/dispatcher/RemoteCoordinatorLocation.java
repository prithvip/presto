package com.facebook.presto.dispatcher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

import javax.ws.rs.core.UriInfo;

import java.net.URI;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Strings.isNullOrEmpty;

public class RemoteCoordinatorLocation
        implements CoordinatorLocation
{

    private final String host;
    private final int port;

    @JsonCreator
    public RemoteCoordinatorLocation(@JsonProperty("host") String host, @JsonProperty("port") int port)
    {
        this.host = host;
        this.port = port;
    }

    @JsonProperty
    public String getHost()
    {
        return host;
    }

    @JsonProperty
    public int getPort()
    {
        return port;
    }

    @Override
    public URI getUri(UriInfo uriInfo, String xForwardedProto)
    {
        String scheme = isNullOrEmpty(xForwardedProto) ? uriInfo.getRequestUri().getScheme() : xForwardedProto;
        return uriInfo.getRequestUriBuilder()
                .scheme(scheme)
                .host(host)
                .port(port)
                .replacePath("")
                .replaceQuery("")
                .build();

    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("host", host)
                .add("port", port)
                .toString();
    }
}
