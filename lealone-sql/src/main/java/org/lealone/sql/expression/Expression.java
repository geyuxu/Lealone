/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.sql.expression;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.lealone.common.exceptions.DbException;
import org.lealone.common.util.StringUtils;
import org.lealone.db.Database;
import org.lealone.db.ServerSession;
import org.lealone.db.expression.ExpressionVisitor;
import org.lealone.db.table.Column;
import org.lealone.db.table.ColumnResolver;
import org.lealone.db.table.TableFilter;
import org.lealone.db.value.DataType;
import org.lealone.db.value.Value;
import org.lealone.db.value.ValueArray;

/**
 * An expression is a operation, a value, or a function in a query.
 */
public abstract class Expression implements org.lealone.db.expression.Expression {

    private boolean addedToFilter;

    /**
     * Return the resulting value for the current row.
     *
     * @param session the session
     * @return the result
     */
    @Override
    public abstract Value getValue(ServerSession session);

    /**
     * Return the data type. The data type may not be known before the
     * optimization phase.
     *
     * @return the type
     */
    @Override
    public abstract int getType();

    /**
     * Map the columns of the resolver to expression columns.
     *
     * @param resolver the column resolver
     * @param level the subquery nesting level
     */
    @Override
    public abstract void mapColumns(ColumnResolver resolver, int level);

    /**
     * Try to optimize the expression.
     *
     * @param session the session
     * @return the optimized expression
     */
    @Override
    public abstract Expression optimize(ServerSession session);

    /**
     * Tell the expression columns whether the table filter can return values now.
     * This is used when optimizing the query.
     *
     * @param tableFilter the table filter
     * @param value true if the table filter can return value
     */
    @Override
    public abstract void setEvaluatable(TableFilter tableFilter, boolean value);

    /**
     * Get the scale of this expression.
     *
     * @return the scale
     */
    @Override
    public abstract int getScale();

    /**
     * Get the precision of this expression.
     *
     * @return the precision
     */
    @Override
    public abstract long getPrecision();

    /**
     * Get the display size of this expression.
     *
     * @return the display size
     */
    @Override
    public abstract int getDisplaySize();

    /**
     * Get the SQL statement of this expression.
     * This may not always be the original SQL statement,
     * specially after optimization.
     *
     * @return the SQL statement
     */
    @Override
    public abstract String getSQL(boolean isDistributed);

    @Override
    public String getSQL() {
        return getSQL(false);
    }

    /**
     * Update an aggregate value.
     * This method is called at statement execution time.
     * It is usually called once for each row, but if the expression is used multiple
     * times (for example in the column list, and as part of the HAVING expression)
     * it is called multiple times - the row counter needs to be used to make sure
     * the internal state is only updated once.
     *
     * @param session the session
     */
    public abstract void updateAggregate(ServerSession session);

    /**
     * Check if this expression and all sub-expressions can fulfill a criteria.
     * If any part returns false, the result is false.
     *
     * @param visitor the visitor
     * @return if the criteria can be fulfilled
     */
    @Override
    public abstract boolean isEverything(ExpressionVisitor visitor);

    /**
     * Estimate the cost to process the expression.
     * Used when optimizing the query, to calculate the query plan
     * with the lowest estimated cost.
     *
     * @return the estimated cost
     */
    public abstract int getCost();

    /**
     * If it is possible, return the negated expression. This is used
     * to optimize NOT expressions: NOT ID>10 can be converted to
     * ID&lt;=10. Returns null if negating is not possible.
     *
     * @param session the session
     * @return the negated expression, or null
     */
    public Expression getNotIfPossible(ServerSession session) {
        // by default it is not possible
        return null;
    }

    /**
     * Check if this expression will always return the same value.
     *
     * @return if the expression is constant
     */
    @Override
    public boolean isConstant() {
        return false;
    }

    /**
     * Is the value of a parameter set.
     *
     * @return true if set
     */
    public boolean isValueSet() {
        return false;
    }

    /**
     * Check if this is an auto-increment column.
     *
     * @return true if it is an auto-increment column
     */
    @Override
    public boolean isAutoIncrement() {
        return false;
    }

    /**
     * Get the value in form of a boolean expression.
     * Returns true, false, or null.
     * In this database, everything can be a condition.
     *
     * @param session the session
     * @return the result
     */
    @Override
    public Boolean getBooleanValue(ServerSession session) {
        return getValue(session).getBoolean();
    }

    /**
     * Create index conditions if possible and attach them to the table filter.
     *
     * @param session the session
     * @param filter the table filter
     */
    @Override
    public void createIndexConditions(ServerSession session, TableFilter filter) {
        // default is do nothing
    }

    /**
     * Get the column name or alias name of this expression.
     *
     * @return the column name
     */
    @Override
    public String getColumnName() {
        return getAlias();
    }

    /**
     * Get the schema name, or null
     *
     * @return the schema name
     */
    @Override
    public String getSchemaName() {
        return null;
    }

    /**
     * Get the table name, or null
     *
     * @return the table name
     */
    @Override
    public String getTableName() {
        return null;
    }

    /**
     * Check whether this expression is a column and can store NULL.
     *
     * @return whether NULL is allowed
     */
    @Override
    public int getNullable() {
        return Column.NULLABLE_UNKNOWN;
    }

    /**
     * Get the alias name of a column or SQL expression
     * if it is not an aliased expression.
     *
     * @return the alias name
     */
    @Override
    public String getAlias() {
        return StringUtils.unEnclose(getSQL());
    }

    /**
     * Only returns true if the expression is a wildcard.
     *
     * @return if this expression is a wildcard
     */
    public boolean isWildcard() {
        return false;
    }

    /**
     * Returns the main expression, skipping aliases.
     *
     * @return the expression
     */
    @Override
    public Expression getNonAliasExpression() {
        return this;
    }

    /**
     * Add conditions to a table filter if they can be evaluated.
     *
     * @param filter the table filter
     * @param outerJoin if the expression is part of an outer join
     */
    @Override
    public void addFilterConditions(TableFilter filter, boolean outerJoin) {
        if (!addedToFilter && !outerJoin && isEverything(ExpressionVisitor.EVALUATABLE_VISITOR)) {
            filter.addFilterCondition(this, false);
            addedToFilter = true;
        }
    }

    /**
     * Convert this expression to a String.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return getSQL();
    }

    /**
     * If this expression consists of column expressions it should return them.
     *
     * @param session the session
     * @return array of expression columns if applicable, null otherwise
     */
    public Expression[] getExpressionColumns(ServerSession session) {
        return null;
    }

    /**
     * Extracts expression columns from ValueArray
     *
     * @param session the current session
     * @param value the value to extract columns from
     * @return array of expression columns
     */
    public static Expression[] getExpressionColumns(ServerSession session, ValueArray value) {
        Value[] list = value.getList();
        ExpressionColumn[] expr = new ExpressionColumn[list.length];
        for (int i = 0, len = list.length; i < len; i++) {
            Value v = list[i];
            Column col = new Column("C" + (i + 1), v.getType(), v.getPrecision(), v.getScale(), v.getDisplaySize());
            expr[i] = new ExpressionColumn(session.getDatabase(), col);
        }
        return expr;
    }

    /**
     * Extracts expression columns from the given result set.
     *
     * @param session the session
     * @param rs the result set
     * @return an array of expression columns
     */
    public static Expression[] getExpressionColumns(ServerSession session, ResultSet rs) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            Expression[] expressions = new Expression[columnCount];
            Database db = session == null ? null : session.getDatabase();
            for (int i = 0; i < columnCount; i++) {
                String name = meta.getColumnLabel(i + 1);
                int type = DataType.convertSQLTypeToValueType(meta.getColumnType(i + 1));
                int precision = meta.getPrecision(i + 1);
                int scale = meta.getScale(i + 1);
                int displaySize = meta.getColumnDisplaySize(i + 1);
                Column col = new Column(name, type, precision, scale, displaySize);
                Expression expr = new ExpressionColumn(db, col);
                expressions[i] = expr;
            }
            return expressions;
        } catch (SQLException e) {
            throw DbException.convert(e);
        }
    }

    public void mergeAggregate(ServerSession session, Value v) {
    }

    public void calculate(Calculator calculator) {
    }

    public Value getMergedValue(ServerSession session) {
        return getValue(session);
    }
}
