/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.orchestration.internal.datasource;

import com.google.common.eventbus.Subscribe;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.shardingsphere.api.config.RuleConfiguration;
import org.apache.shardingsphere.core.config.DataSourceConfiguration;
import org.apache.shardingsphere.core.constant.ShardingConstant;
import org.apache.shardingsphere.orchestration.internal.eventbus.ShardingOrchestrationEventBus;
import org.apache.shardingsphere.orchestration.internal.registry.ShardingOrchestrationFacade;
import org.apache.shardingsphere.orchestration.internal.registry.state.event.CircuitStateChangedEvent;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.AbstractDataSourceAdapter;
import org.apache.shardingsphere.shardingjdbc.jdbc.unsupported.AbstractUnsupportedOperationDataSource;
import org.apache.shardingsphere.shardingjdbc.orchestration.internal.circuit.datasource.CircuitBreakerDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Abstract orchestration data source.
 *
 * @author panjuan
 */
public abstract class AbstractOrchestrationDataSource extends AbstractUnsupportedOperationDataSource implements AutoCloseable {
    
    @Getter
    @Setter
    private PrintWriter logWriter = new PrintWriter(System.out);
    
    @Getter(AccessLevel.PROTECTED)
    private final ShardingOrchestrationFacade shardingOrchestrationFacade;
    
    private boolean isCircuitBreak;
    
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, DataSourceConfiguration> dataSourceConfigurations = new LinkedHashMap<>();
    
    public AbstractOrchestrationDataSource(final ShardingOrchestrationFacade shardingOrchestrationFacade) {
        this.shardingOrchestrationFacade = shardingOrchestrationFacade;
        ShardingOrchestrationEventBus.getInstance().register(this);
    }
    
    protected abstract DataSource getDataSource();
    
    @Override
    public final Connection getConnection() throws SQLException {
        return isCircuitBreak ? new CircuitBreakerDataSource().getConnection() : getDataSource().getConnection();
    }
    
    @Override
    @SneakyThrows
    public final Connection getConnection(final String username, final String password) {
        return getConnection();
    }
    
    @Override
    public final Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
    
    @Override
    public final void close() throws Exception {
        ((AbstractDataSourceAdapter) getDataSource()).close();
        shardingOrchestrationFacade.close();
    }
    
    /**
     /**
     * Renew circuit breaker state.
     *
     * @param circuitStateChangedEvent circuit state changed event
     */
    @Subscribe
    public final synchronized void renew(final CircuitStateChangedEvent circuitStateChangedEvent) {
        isCircuitBreak = circuitStateChangedEvent.isCircuitBreak();
    }
    
    protected final void initShardingOrchestrationFacade() {
        shardingOrchestrationFacade.init();
        dataSourceConfigurations.putAll(shardingOrchestrationFacade.getConfigService().loadDataSourceConfigurations(ShardingConstant.LOGIC_SCHEMA_NAME));
    }
    
    protected final void initShardingOrchestrationFacade(
            final Map<String, Map<String, DataSourceConfiguration>> dataSourceConfigurations, final Map<String, RuleConfiguration> schemaRuleMap, final Properties props) {
        shardingOrchestrationFacade.init(dataSourceConfigurations, schemaRuleMap, null, props);
        this.dataSourceConfigurations.putAll(dataSourceConfigurations.get(ShardingConstant.LOGIC_SCHEMA_NAME));
    }
}
