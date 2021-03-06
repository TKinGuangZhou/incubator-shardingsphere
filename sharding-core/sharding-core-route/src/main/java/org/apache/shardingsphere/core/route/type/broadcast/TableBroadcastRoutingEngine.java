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

package org.apache.shardingsphere.core.route.type.broadcast;

import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.SQLStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.ddl.DDLStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.token.IndexToken;
import org.apache.shardingsphere.core.parse.antlr.sql.token.SQLToken;
import org.apache.shardingsphere.core.route.type.RoutingEngine;
import org.apache.shardingsphere.core.route.type.RoutingResult;
import org.apache.shardingsphere.core.route.type.RoutingTable;
import org.apache.shardingsphere.core.route.type.TableUnit;
import org.apache.shardingsphere.core.rule.DataNode;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.rule.TableRule;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Broadcast routing engine for tables.
 * 
 * @author zhangliang
 * @author maxiaoguang
 * @author panjuan
 */
@RequiredArgsConstructor
public final class TableBroadcastRoutingEngine implements RoutingEngine {
    
    private final ShardingRule shardingRule;
    
    private final SQLStatement sqlStatement;
    
    @Override
    public RoutingResult route() {
        RoutingResult result = new RoutingResult();
        for (String each : getLogicTableNames()) {
            result.getTableUnits().getTableUnits().addAll(getAllTableUnits(each));
        }
        return result;
    }
    
    private Collection<String> getLogicTableNames() {
        if (isOperateIndexWithoutTable()) {
            String indexName = sqlStatement.getLogicSQL().substring(getIndexToken().getStartIndex(), getIndexToken().getStopIndex() + 1);
            return Collections.singletonList(shardingRule.getLogicTableName(indexName));
        }
        return sqlStatement.getTables().getTableNames();
    }
    
    private boolean isOperateIndexWithoutTable() {
        return sqlStatement instanceof DDLStatement && sqlStatement.getTables().isEmpty();
    }
    
    private IndexToken getIndexToken() {
        List<SQLToken> sqlTokens = sqlStatement.getSQLTokens();
        Preconditions.checkState(1 == sqlTokens.size());
        return (IndexToken) sqlTokens.get(0);
    }
    
    private Collection<TableUnit> getAllTableUnits(final String logicTableName) {
        Collection<TableUnit> result = new LinkedList<>();
        TableRule tableRule = shardingRule.getTableRule(logicTableName);
        for (DataNode each : tableRule.getActualDataNodes()) {
            TableUnit tableUnit = new TableUnit(each.getDataSourceName());
            tableUnit.getRoutingTables().add(new RoutingTable(logicTableName, each.getTableName()));
            result.add(tableUnit);
        }
        return result;
    }
}
