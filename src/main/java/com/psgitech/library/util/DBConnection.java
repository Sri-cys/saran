package com.psgitech.library.util;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Database connection manager for PSG iTech Library System.
 * Connects to MySQL (psgitech_library) via Tomcat JNDI connection pool when available,
 * falling back to direct DriverManager for standalone repository tests.
 * Supports development fallback only when explicitly enabled via configuration.
 */
public class DBConnection {
    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());

    private static volatile String dbUrl;
    private static volatile String dbDriver;
    private static volatile String dbUsername;
    private static volatile String dbPassword;

    private static volatile String fallbackUrl;
    private static volatile String fallbackDriver;
    private static volatile String fallbackUsername;
    private static volatile String fallbackPassword;

    private static volatile boolean fallbackEnabled = false;
    private static volatile boolean usingFallback = false;
    private static volatile boolean initialized = false;

    private static volatile DataSource jndiDataSource = null;
    private static volatile boolean jndiAttempted = false;

    static {
        loadConfiguration();
    }

    private static void loadConfiguration() {
        Properties props = new Properties();
        try (InputStream is = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load db.properties, using defaults", e);
        }

        dbUrl = System.getProperty("db.url", props.getProperty("db.url", "jdbc:mysql://localhost:3306/psgitech_library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"));
        dbDriver = System.getProperty("db.driver", props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver"));
        dbUsername = System.getProperty("db.username", props.getProperty("db.username", "root"));
        dbPassword = System.getProperty("db.password", props.getProperty("db.password", "root"));

        fallbackEnabled = Boolean.parseBoolean(System.getProperty("db.fallback.enabled", props.getProperty("db.fallback.enabled", "false")));
        fallbackUrl = props.getProperty("db.fallback.url", "jdbc:h2:mem:psgitech_library;MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
        fallbackDriver = props.getProperty("db.fallback.driver", "org.h2.Driver");
        fallbackUsername = props.getProperty("db.fallback.username", "sa");
        fallbackPassword = props.getProperty("db.fallback.password", "");
    }

    private static DataSource getJndiDataSource() {
        if (!jndiAttempted) {
            synchronized (DBConnection.class) {
                if (!jndiAttempted) {
                    try {
                        InitialContext initCtx = new InitialContext();
                        Context envCtx = (Context) initCtx.lookup("java:comp/env");
                        jndiDataSource = (DataSource) envCtx.lookup("jdbc/PsgItechDB");
                        LOGGER.info("Bound to Tomcat JNDI DataSource: jdbc/PsgItechDB");
                    } catch (NamingException e) {
                        LOGGER.fine("JNDI DataSource not found (" + e.getMessage() + "), using direct DriverManager connection");
                    } finally {
                        jndiAttempted = true;
                    }
                }
            }
        }
        return jndiDataSource;
    }

    /**
     * Obtains a JDBC Connection.
     * Thread-safe, non-blocking connection acquisition using JNDI pool with DriverManager fallback.
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            synchronized (DBConnection.class) {
                if (!initialized) {
                    initializeDatabase();
                    initialized = true;
                }
            }
        }

        if (usingFallback) {
            try {
                Class.forName(fallbackDriver);
                return DriverManager.getConnection(fallbackUrl, fallbackUsername, fallbackPassword);
            } catch (ClassNotFoundException e) {
                throw new SQLException("Fallback JDBC Driver not found: " + fallbackDriver, e);
            }
        }

        // Try JNDI connection pool first (inside Tomcat)
        DataSource ds = getJndiDataSource();
        if (ds != null) {
            Connection conn = ds.getConnection();
            try {
                if (conn.isReadOnly()) {
                    conn.setReadOnly(false);
                }
            } catch (SQLException ignored) {}
            return conn;
        }

        // Direct DriverManager fallback (in unit / integration test JVMs)
        try {
            Class.forName(dbDriver);
            Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            try {
                if (conn.isReadOnly()) {
                    conn.setReadOnly(false);
                }
            } catch (SQLException ignored) {}
            return conn;
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found: " + dbDriver, e);
        }
    }

    private static synchronized void enableFallback() {
        usingFallback = true;
        try {
            Class.forName(fallbackDriver);
            try (Connection conn = DriverManager.getConnection(fallbackUrl, fallbackUsername, fallbackPassword)) {
                executeSchemaScript(conn, "schema.sql");
                executeSchemaScript(conn, "seed_data.sql");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize fallback database", ex);
        }
    }

    public static synchronized void initializeDatabase() {
        if (initialized) return;
        initialized = true;

        if (usingFallback) {
            enableFallback();
            return;
        }

        try {
            Class.forName(dbDriver);
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
                LOGGER.info("Connected to primary MySQL database at: " + dbUrl);
                executeSchemaScript(conn, "schema.sql");
                return;
            }
        } catch (Exception e) {
            if (fallbackEnabled) {
                LOGGER.info("Primary MySQL database unavailable (" + e.getMessage() + ") and fallback enabled, initializing development database");
                enableFallback();
            } else {
                LOGGER.log(Level.SEVERE, "Primary MySQL database unavailable: " + e.getMessage() + ". Development fallback disabled (db.fallback.enabled=false).", e);
            }
        }
    }

    private static void executeSchemaScript(Connection conn, String scriptPath) {
        try (InputStream is = DBConnection.class.getClassLoader().getResourceAsStream(scriptPath)) {
            if (is == null) {
                LOGGER.warning("Script " + scriptPath + " not found on classpath");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder sql = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("--") || line.startsWith("#")) {
                        continue;
                    }
                    sql.append(line).append(" ");
                    if (line.endsWith(";")) {
                        String statement = sql.toString().replace(";", "").trim();
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(statement);
                        } catch (SQLException sqle) {
                            LOGGER.fine("SQL notice on statement: " + sqle.getMessage());
                        }
                        sql.setLength(0);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing " + scriptPath, e);
        }
    }

    /**
     * Checks if a SQLException is caused by pool exhaustion or MySQL max_connections (1040).
     */
    public static boolean isPoolExhaustedOrMaxConnections(SQLException e) {
        if (e == null) return false;
        if (e.getErrorCode() == 1040) return true; // ER_CON_COUNT_ERROR
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("pool empty") || msg.contains("timeout waiting for idle object")
                || msg.contains("pool exhausted") || msg.contains("unable to fetch a connection")
                || msg.contains("cannot get a connection, pool error")
                || msg.contains("too many connections");
    }

    public static boolean isUsingFallback() {
        return usingFallback;
    }

    public static void setForceFallback(boolean force) {
        usingFallback = force;
        initialized = false;
    }

    public static void resetJndiDataSource() {
        synchronized (DBConnection.class) {
            jndiDataSource = null;
            jndiAttempted = false;
            initialized = false;
            loadConfiguration();
        }
    }

    public static String getDbUrl() {
        return dbUrl;
    }
}
