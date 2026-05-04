package com.acme.modres;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Health check endpoint for container orchestration platforms (ECS/EKS).
 * Returns HTTP 200 with JSON status when the application is healthy.
 * This endpoint is used by load balancers and container health checks.
 */
@WebServlet({ "/health", "/actuator/health" })
public class HealthCheckServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    
    // Perform basic health checks
    boolean isHealthy = performHealthChecks();
    
    if (isHealthy) {
      response.setStatus(HttpServletResponse.SC_OK);
      PrintWriter out = response.getWriter();
      out.print("{\"status\":\"UP\",\"application\":\"ModResorts\",\"version\":\"2.0.0\"}");
      out.flush();
    } else {
      response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      PrintWriter out = response.getWriter();
      out.print("{\"status\":\"DOWN\",\"application\":\"ModResorts\",\"version\":\"2.0.0\"}");
      out.flush();
    }
  }
  
  /**
   * Perform basic health checks.
   * In production, add checks for:
   * - Database connectivity
   * - External service availability
   * - Memory/resource availability
   */
  private boolean performHealthChecks() {
    try {
      // Basic check - if servlet is responding, application is running
      // Add more sophisticated checks as needed
      Runtime runtime = Runtime.getRuntime();
      long freeMemory = runtime.freeMemory();
      long totalMemory = runtime.totalMemory();
      
      // Check if we have at least 10% free memory
      double freeMemoryPercent = (double) freeMemory / totalMemory;
      
      return freeMemoryPercent > 0.1;
    } catch (Exception e) {
      return false;
    }
  }
  
  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    // Support HEAD requests for simple health checks
    response.setStatus(HttpServletResponse.SC_OK);
  }
}
