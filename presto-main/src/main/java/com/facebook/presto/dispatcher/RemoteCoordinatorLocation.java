/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.dispatcher;

import javax.ws.rs.core.UriInfo;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;

public class RemoteCoordinatorLocation
        implements CoordinatorLocation
{
    private final URL url;

    public RemoteCoordinatorLocation(URL url)
    {
        this.url = url;
    }

    public RemoteCoordinatorLocation(URI uri)
    {
        try {
            this.url = uri.toURL();
        }
        catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public URI getUri(UriInfo uriInfo, String xForwardedProto)
    {
        String scheme = isNullOrEmpty(xForwardedProto) ? uriInfo.getRequestUri().getScheme() : xForwardedProto;
        return uriInfo.getRequestUriBuilder()
                .scheme(scheme)
                .host(url.getHost())
                .port(url.getPort())
                .replacePath("")
                .replaceQuery("")
                .build();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RemoteCoordinatorLocation that = (RemoteCoordinatorLocation) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(url);
    }
}
