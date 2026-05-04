package com.acme.modres.db;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Migrated from singleton state storage to support distributed caching.
 * In production, integrate with Amazon ElastiCache (Redis) using Spring Cache abstraction
 * for consistency across horizontally scaled container instances.
 * 
 * For now, using ConcurrentHashMap as a placeholder. In containerized deployment:
 * 1. Add spring-boot-starter-data-redis dependency
 * 2. Configure Redis connection via environment variables (REDIS_HOST, REDIS_PORT)
 * 3. Use @Cacheable annotations for distributed caching
 */
@Singleton
@Startup
public class ModResortsCustomerInformation {
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  // Removing DB connection for ease of demo setup
  // @Resource(lookup = "jdbc/ModResortsJndi")
  private DataSource dataSource;

  // Replaced singleton state with cache that can be externalized to Redis
  // In production: Use Spring Cache with Redis backend
  private Map<String, ArrayList<String>> customerCache = new ConcurrentHashMap<>();
  
  @PostConstruct
  public void init() {
    // Initialize cache - in production this would connect to Redis
    // Configuration via environment variables:
    // REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
  }

  public ArrayList<String> getCustomerInformation() {
    // Check cache first (in production, this would be Redis)
    String cacheKey = "all_customers";
    if (customerCache.containsKey(cacheKey)) {
      return customerCache.get(cacheKey);
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

      // Store in cache (in production, this would be Redis with TTL)
      customerCache.put(cacheKey, customerInfo);

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
  
  /**
   * Clear cache - useful for cache invalidation
   * In production with Redis, use cache eviction strategies
   */
  public void clearCache() {
    customerCache.clear();
  }
}
