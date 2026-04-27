package com.acme.modres;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.util.HtmlUtils;

/**
 * Cloud-native servlet using standard Spring utilities.
 * Removes WebSphere-specific dependencies.
 * Fixes blocker: cr-java-0116
 */
@WebServlet("/resorts/upper")
public class UpperServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = Logger.getLogger(UpperServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setContentType("text/html");

    String originalStr = request.getParameter("input");
    if (originalStr == null) {
      originalStr = "";
    }

    String newStr = originalStr.toUpperCase();
    // Use Spring's HtmlUtils instead of WebSphere-specific ResponseUtils
    newStr = HtmlUtils.htmlEscape(newStr);

    PrintWriter out = response.getWriter();
    out.print("<br/><b>upper case input " + newStr + "</b>");
    
    logger.info("Processed upper case conversion for input: " + originalStr);
  }
}
