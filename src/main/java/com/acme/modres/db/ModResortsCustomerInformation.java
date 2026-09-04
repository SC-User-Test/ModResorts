package com.acme.modres.db;

import jakarta.annotation.Resource;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Provides customer information from the PostgreSQL database.
 *
 * Migrated from SQL Server to PostgreSQL 16:
 * - DataSource lookup updated to use PostgreSQL driver (org.postgresql.ds.PGSimpleDataSource)
 * - SQL query updated to use lowercase table/column names (PostgreSQL is case-sensitive by default)
 * - @DataSourceDefinition added for container-managed PostgreSQL datasource configuration
 * - javax.sql.DataSource retained (still valid in Jakarta EE 10 via JDK)
 */
@Singleton
@Startup
@DataSourceDefinition(
    name = "java:app/jdbc/ModResortsDS",
    className = "org.postgresql.ds.PGSimpleDataSource",
    serverName = "${env.PGHOST:localhost}",
    portNumber = 5432,
    databaseName = "${env.PGDATABASE:modresorts}",
    user = "${env.PGUSER:postgres}",
    password = "${env.PGPASSWORD:}",
    properties = {
        "connectionTimeout=30",
        "ssl=false"
    }
)
public class ModResortsCustomerInformation {

  // PostgreSQL-compatible query: lowercase table/column names
  // (PostgreSQL treats unquoted identifiers as lowercase; SQL Server is case-insensitive)
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT info FROM customer";

  @Resource(lookup = "java:app/jdbc/ModResortsDS")
  private DataSource dataSource;

  public ArrayList<String> getCustomerInformation() {
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    ArrayList<String> customerInfo = new ArrayList<>();

    try {
      // Get a connection from the injected data source
      conn = dataSource.getConnection();
      // Create a prepared statement
      stmt = conn.prepareStatement(SELECT_CUSTOMERS_QUERY);
      // Execute the query
      rs = stmt.executeQuery();

      // Process the results
      while (rs.next()) {
        String info = rs.getString("info");
        customerInfo.add(info);
      }

    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      // Close the result set, statement, and connection
      try {
        if (rs != null)
          rs.close();
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return customerInfo;
  }
}
