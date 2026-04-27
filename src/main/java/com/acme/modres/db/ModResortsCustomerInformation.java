package com.acme.modres.db;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

// Replace @Singleton with @ApplicationScoped for better container compatibility
// State should be externalized to distributed cache (Redis/ElastiCache) for horizontal scaling
@ApplicationScoped
@Startup
public class ModResortsCustomerInformation {
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  // Removing DB connection for ease of demo setup
  // @Resource(lookup = "jdbc/ModResortsJndi")
  private DataSource dataSource;

  // For distributed caching in containerized environments, use:
  // - Amazon ElastiCache for Redis with Spring Cache abstraction
  // - Configure via environment variables: ${REDIS_HOST}, ${REDIS_PORT}
  // - Use @Cacheable annotations for method-level caching
  // This ensures consistency across horizontally scaled container instances
  
  // Temporary in-memory cache (should be replaced with Redis in production)
  private Map<String, ArrayList<String>> cache = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    // Initialize cache or connect to distributed cache service
    // In production, configure Redis connection using environment variables:
    // String redisHost = System.getenv("REDIS_HOST");
    // String redisPort = System.getenv("REDIS_PORT");
  }

  public ArrayList<String> getCustomerInformation() {
    // Check cache first
    String cacheKey = "customer_info";
    if (cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }

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
        String info = rs.getString("INFO");
        customerInfo.add(info);
      }

      // Store in cache
      cache.put(cacheKey, customerInfo);

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

  // Method to clear cache (useful for cache invalidation)
  public void clearCache() {
    cache.clear();
  }
}
