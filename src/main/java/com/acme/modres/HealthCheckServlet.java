package com.acme.modres;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Health check endpoint for containerized deployment.
 * Returns application health status for container orchestration platforms (ECS/EKS).
 */
@WebServlet({ "/health", "/actuator/health" })
public class HealthCheckServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    
    // Basic health check - returns 200 OK if application is running
    // For production, add checks for:
    // - Database connectivity
    // - External service availability
    // - Memory/resource thresholds
    
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
   * Perform application health checks.
   * Override this method to add custom health checks.
   */
  private boolean performHealthChecks() {
    // Basic check - if servlet is responding, application is up
    // Add additional checks as needed:
    // - Database connection pool status
    // - External API availability
    // - Disk space, memory usage, etc.
    return true;
  }
  
  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    // Support HEAD requests for lightweight health checks
    boolean isHealthy = performHealthChecks();
    response.setStatus(isHealthy ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
  }
}
