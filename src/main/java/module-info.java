/**
 * ModResorts Application Module
 * Java 17 Module Descriptor
 */
module com.acme.modresorts {
    // Jakarta EE modules
    requires jakarta.servlet;
    requires jakarta.inject;
    requires jakarta.ejb;
    requires jakarta.annotation;
    
    // Java SE modules
    requires java.naming;
    requires java.sql;
    requires java.management;
    requires java.logging;
    
    // Third-party modules
    requires com.google.gson;
    
    // Export packages for external access
    exports com.acme.modres;
    exports com.acme.modres.db;
    exports com.acme.modres.exception;
    exports com.acme.modres.mbean;
    exports com.acme.modres.mbean.reservation;
    exports com.acme.modres.security;
    exports com.acme.modres.util;
}
