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
package com.facebook.presto.functionNamespace.execution;

import com.facebook.presto.common.Page;
import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.functionNamespace.execution.thrift.ThriftSqlFunctionExecutor;
import com.facebook.presto.spi.function.FunctionImplementationType;
import com.facebook.presto.spi.function.RoutineCharacteristics.Language;
import com.facebook.presto.spi.function.ScalarFunctionImplementation;
import com.facebook.presto.spi.function.ThriftScalarFunctionImplementation;
import com.google.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class SqlFunctionExecutors
{
    private final Map<Language, FunctionImplementationType> supportedLanguages;
    private final ThriftSqlFunctionExecutor thriftSqlFunctionExecutor;

    @Inject
    public SqlFunctionExecutors(Map<Language, FunctionImplementationType> supportedLanguages, ThriftSqlFunctionExecutor thriftSqlFunctionExecutor)
    {
        this.supportedLanguages = requireNonNull(supportedLanguages, "supportedLanguages is null");
        this.thriftSqlFunctionExecutor = requireNonNull(thriftSqlFunctionExecutor, "thriftSqlFunctionExecutor is null");
    }

    public Set<Language> getSupportedLanguages()
    {
        return supportedLanguages.keySet();
    }

    public FunctionImplementationType getFunctionImplementationType(Language language)
    {
        return FunctionImplementationType.THRIFT;
        // return supportedLanguages.get(language);
    }

    public CompletableFuture<Block> executeFunction(ScalarFunctionImplementation functionImplementation, Page input, List<Integer> channels, List<Type> argumentTypes, Type returnType)
    {
        checkArgument(functionImplementation instanceof ThriftScalarFunctionImplementation, format("Only support ThriftScalarFunctionImplementation, got %s", functionImplementation.getClass()));
        return thriftSqlFunctionExecutor.executeFunction((ThriftScalarFunctionImplementation) functionImplementation, input, channels, argumentTypes, returnType);
    }
}
