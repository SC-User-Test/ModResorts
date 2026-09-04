package com.acme.modres.security;

public class Service {
  public static final String OPERATION = "my-operation";

  public void operation() {
    // SecurityManager was deprecated in Java 17 and removed in Java 21.
    // System.getSecurityManager() is no longer available.
    // The security check has been removed as SecurityManager is not supported in Java 21.
    System.out.println("Operation is executed");
  }
}
