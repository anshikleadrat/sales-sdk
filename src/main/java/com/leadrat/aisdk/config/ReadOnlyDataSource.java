package com.leadrat.aisdk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Set;

public class ReadOnlyDataSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyDataSource.class);

    private static final Set<String> SQL_EXECUTING_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch", "nativeSQL");

    private final DataSource delegate;
    private final int statementTimeoutMs;
    private volatile Boolean postgres;

    public ReadOnlyDataSource(DataSource delegate, int statementTimeoutMs) {
        this.delegate = delegate;
        this.statementTimeoutMs = Math.max(1000, statementTimeoutMs);
    }

    public DataSource delegate() {
        return delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return guard(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return guard(delegate.getConnection(username, password));
    }

    private Connection guard(Connection raw) throws SQLException {
        boolean pg = isPostgres(raw);
        try {
            raw.setReadOnly(true);
            if (pg) {
                execute(raw, "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
                execute(raw, "SET statement_timeout = " + statementTimeoutMs);
                execute(raw, "SET idle_in_transaction_session_timeout = " + (statementTimeoutMs * 2L));
            }
        } catch (SQLException e) {
            try {
                raw.close();
            } catch (SQLException ignored) {
                // connection is being discarded
            }
            throw e;
        }
        return (Connection) Proxy.newProxyInstance(ReadOnlyDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class}, new GuardedConnection(raw, pg));
    }

    private boolean isPostgres(Connection raw) {
        Boolean cached = postgres;
        if (cached != null) {
            return cached;
        }
        boolean detected;
        try {
            String product = raw.getMetaData().getDatabaseProductName();
            detected = product != null && product.toLowerCase().contains("postgres");
        } catch (SQLException e) {
            detected = false;
        }
        postgres = detected;
        if (!detected) {
            log.warn("ai-sdk: read-only session enforcement at the database level is only applied for PostgreSQL; "
                    + "falling back to JDBC read-only mode plus SQL statement guarding");
        }
        return detected;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private final class GuardedConnection implements InvocationHandler {

        private final Connection raw;
        private final boolean postgresConnection;
        private Connection proxy;
        private boolean closed;

        private GuardedConnection(Connection raw, boolean postgresConnection) {
            this.raw = raw;
            this.postgresConnection = postgresConnection;
        }

        @Override
        public Object invoke(Object proxyInstance, Method method, Object[] args) throws Throwable {
            this.proxy = (Connection) proxyInstance;
            String name = method.getName();
            switch (name) {
                case "hashCode" -> {
                    return System.identityHashCode(proxyInstance);
                }
                case "equals" -> {
                    return proxyInstance == (args == null ? null : args[0]);
                }
                case "toString" -> {
                    return "ReadOnlyConnection[" + raw + "]";
                }
                case "close" -> {
                    close();
                    return null;
                }
                case "isClosed" -> {
                    return closed || raw.isClosed();
                }
                case "setReadOnly" -> {
                    if (args != null && Boolean.FALSE.equals(args[0])) {
                        throw new ReadOnlyViolationException(
                                "ai-sdk read-only guardrail: attempt to disable read-only mode was blocked");
                    }
                    return null;
                }
                case "prepareCall" -> throw new ReadOnlyViolationException(
                        "ai-sdk read-only guardrail: callable statements are not permitted");
                default -> {
                }
            }
            if (args != null && args.length > 0 && args[0] instanceof String sql
                    && (name.equals("prepareStatement") || name.equals("nativeSQL"))) {
                ReadOnlySqlGuard.verify(sql);
            }
            Object result = call(method, args);
            if (result instanceof CallableStatement) {
                throw new ReadOnlyViolationException(
                        "ai-sdk read-only guardrail: callable statements are not permitted");
            }
            if (result instanceof PreparedStatement statement) {
                return wrapStatement(statement, PreparedStatement.class);
            }
            if (result instanceof Statement statement) {
                return wrapStatement(statement, Statement.class);
            }
            return result;
        }

        private Object wrapStatement(Statement statement, Class<?> type) {
            return Proxy.newProxyInstance(ReadOnlyDataSource.class.getClassLoader(), new Class<?>[]{type},
                    (statementProxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("getConnection")) {
                            return proxy;
                        }
                        if (args != null && args.length > 0 && args[0] instanceof String sql
                                && SQL_EXECUTING_METHODS.contains(name)) {
                            ReadOnlySqlGuard.verify(sql);
                        }
                        try {
                            return method.invoke(statement, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        private Object call(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(raw, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private void close() throws SQLException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (!raw.isClosed()) {
                    restore();
                }
            } finally {
                raw.close();
            }
        }

        private void restore() {
            try {
                if (!raw.getAutoCommit()) {
                    raw.rollback();
                }
            } catch (SQLException e) {
                log.debug("ai-sdk: rollback on read-only connection release failed ({})", e.toString());
            }
            try {
                if (postgresConnection) {
                    execute(raw, "SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE");
                    execute(raw, "SET statement_timeout = DEFAULT");
                    execute(raw, "SET idle_in_transaction_session_timeout = DEFAULT");
                }
                raw.setReadOnly(false);
            } catch (SQLException e) {
                log.warn("ai-sdk: could not reset session state on a pooled connection; it will be discarded ({})",
                        e.toString());
                try {
                    raw.abort(Runnable::run);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}
