package com.acme.modres;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Cloud-native logout servlet using Spring Session with Azure Cache for Redis.
 * Removes WebSphere-specific dependencies.
 * Fixes blocker: cr-java-0116
 */
@WebServlet({ "/logout" })
public class LogoutServlet extends HttpServlet {
  
  private static final long serialVersionUID = 1L;
  private static final Logger logger = Logger.getLogger(LogoutServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    try {
      // Use standard servlet session management (backed by Spring Session + Azure Redis)
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
        logger.info("User session invalidated successfully");
      }
    } catch (Exception e) {
      logger.severe("Error logging out: " + e.getMessage());
      e.printStackTrace();
    }

    response.sendRedirect("login.jsp");
  }
}
