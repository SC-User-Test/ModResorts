package com.acme.modres.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Cloud-native service using Spring Boot with HikariCP connection pooling.
 * Migrated from EJB 2.x to Spring Boot microservices.
 * Fixes blockers: cr-java-0085
 */
@Service
public class ModResortsCustomerInformation {
  
  private static final Logger logger = Logger.getLogger(ModResortsCustomerInformation.class.getName());
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  @Autowired(required = false)
  private DataSource dataSource;

  public ArrayList<String> getCustomerInformation() {
    ArrayList<String> customerInfo = new ArrayList<>();
    
    if (dataSource == null) {
      logger.warning("DataSource not configured, returning empty customer information");
      return customerInfo;
    }

    // Use try-with-resources for automatic resource management (HikariCP handles connection pooling)
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SELECT_CUSTOMERS_QUERY);
         ResultSet rs = stmt.executeQuery()) {

      // Process the results
      while (rs.next()) {
        String info = rs.getString("INFO");
        customerInfo.add(info);
      }
      
      logger.info("Successfully retrieved " + customerInfo.size() + " customer records");

    } catch (SQLException e) {
      logger.severe("Error retrieving customer information: " + e.getMessage());
      e.printStackTrace();
    }
    
    return customerInfo;
  }
}
