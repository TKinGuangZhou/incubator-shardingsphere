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

package org.apache.shardingsphere.core.parse.antlr.filler.sharding.dml.insert;

import com.google.common.base.Optional;
import lombok.Setter;
import org.apache.shardingsphere.core.parse.antlr.filler.api.SQLSegmentFiller;
import org.apache.shardingsphere.core.parse.antlr.filler.api.ShardingRuleAwareFiller;
import org.apache.shardingsphere.core.parse.antlr.sql.segment.dml.InsertValuesSegment;
import org.apache.shardingsphere.core.parse.antlr.sql.segment.dml.expr.CommonExpressionSegment;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.SQLStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.dml.InsertStatement;
import org.apache.shardingsphere.core.parse.old.parser.context.condition.AndCondition;
import org.apache.shardingsphere.core.parse.old.parser.context.condition.Column;
import org.apache.shardingsphere.core.parse.old.parser.context.condition.Condition;
import org.apache.shardingsphere.core.parse.old.parser.context.condition.GeneratedKeyCondition;
import org.apache.shardingsphere.core.parse.old.parser.context.insertvalue.InsertValue;
import org.apache.shardingsphere.core.parse.old.parser.exception.SQLParsingException;
import org.apache.shardingsphere.core.parse.old.parser.expression.SQLExpression;
import org.apache.shardingsphere.core.parse.old.parser.expression.SQLPlaceholderExpression;
import org.apache.shardingsphere.core.rule.ShardingRule;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Insert values filler.
 *
 * @author zhangliang
 */
@Setter
public final class InsertValuesFiller implements SQLSegmentFiller<InsertValuesSegment>, ShardingRuleAwareFiller {
    
    private ShardingRule shardingRule;
    
    @Override
    public void fill(final InsertValuesSegment sqlSegment, final SQLStatement sqlStatement) {
        InsertStatement insertStatement = (InsertStatement) sqlStatement;
        removeGenerateKeyColumn(insertStatement, shardingRule, sqlSegment.getValues().size());
        AndCondition andCondition = new AndCondition();
        Iterator<String> columnNames = insertStatement.getColumnNames().iterator();
        int parametersCount = 0;
        List<SQLExpression> columnValues = new LinkedList<>();
        for (CommonExpressionSegment each : sqlSegment.getValues()) {
            SQLExpression columnValue = getColumnValue(insertStatement, shardingRule, andCondition, columnNames.next(), each);
            columnValues.add(columnValue);
            if (columnValue instanceof SQLPlaceholderExpression) {
                parametersCount++;
            }
        }
        insertStatement.getRouteConditions().getOrCondition().getAndConditions().add(andCondition);
        InsertValue insertValue = new InsertValue(parametersCount, columnValues);
        insertStatement.getValues().add(insertValue);
        insertStatement.setParametersIndex(insertStatement.getParametersIndex() + insertValue.getParametersCount());
    }
    
    private void removeGenerateKeyColumn(final InsertStatement insertStatement, final ShardingRule shardingRule, final int valuesCount) {
        Optional<String> generateKeyColumnName = shardingRule.findGenerateKeyColumnName(insertStatement.getTables().getSingleTableName());
        if (generateKeyColumnName.isPresent() && valuesCount < insertStatement.getColumnNames().size()) {
            insertStatement.getColumnNames().remove(generateKeyColumnName.get());
        }
    }
    
    private SQLExpression getColumnValue(final InsertStatement insertStatement,
                                         final ShardingRule shardingRule, final AndCondition andCondition, final String columnName, final CommonExpressionSegment expressionSegment) {
        SQLExpression result = expressionSegment.getSQLExpression(insertStatement.getLogicSQL());
        fillShardingCondition(shardingRule, andCondition, insertStatement.getTables().getSingleTableName(), columnName, expressionSegment, result);
        fillGeneratedKeyCondition(insertStatement, insertStatement.getLogicSQL(), shardingRule, columnName, expressionSegment);
        return result;
    }
    
    private void fillShardingCondition(final ShardingRule shardingRule, final AndCondition andCondition, 
                                       final String tableName, final String columnName, final CommonExpressionSegment expressionSegment, final SQLExpression sqlExpression) {
        if (shardingRule.isShardingColumn(columnName, tableName)) {
            if (!(-1 < expressionSegment.getPlaceholderIndex() || null != expressionSegment.getLiterals())) {
                throw new SQLParsingException("INSERT INTO can not support complex expression value on sharding column '%s'.", columnName);
            }
            andCondition.getConditions().add(new Condition(new Column(columnName, tableName), sqlExpression));
        }
    }
    
    private void fillGeneratedKeyCondition(final InsertStatement insertStatement, 
                                           final String sql, final ShardingRule shardingRule, final String columnName, final CommonExpressionSegment expressionSegment) {
        Optional<String> generateKeyColumnName = shardingRule.findGenerateKeyColumnName(insertStatement.getTables().getSingleTableName());
        if (generateKeyColumnName.isPresent() && generateKeyColumnName.get().equalsIgnoreCase(columnName)) {
            insertStatement.getGeneratedKeyConditions().add(createGeneratedKeyCondition(new Column(columnName, insertStatement.getTables().getSingleTableName()), expressionSegment, sql));
        }
    }
    
    private GeneratedKeyCondition createGeneratedKeyCondition(final Column column, final CommonExpressionSegment sqlExpression, final String sql) {
        if (-1 != sqlExpression.getPlaceholderIndex()) {
            return new GeneratedKeyCondition(column, sqlExpression.getPlaceholderIndex(), null);
        }
        if (null != sqlExpression.getLiterals()) {
            return new GeneratedKeyCondition(column, -1, (Comparable<?>) sqlExpression.getLiterals());
        }
        return new GeneratedKeyCondition(column, -1, sql.substring(sqlExpression.getStartIndex(), sqlExpression.getStopIndex() + 1));
    }
}
