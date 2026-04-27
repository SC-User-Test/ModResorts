# ModResorts - Cloud-Native Migration for Azure

## Overview
This application has been migrated from a traditional Java EE application to a cloud-native Spring Boot application optimized for Azure deployment.

## Cloud Readiness Fixes Applied

### 1. **File System Dependencies → Azure Blob Storage**
- **Blockers Fixed**: cr-java-0061, cr-java-0062, cr-java-0063, cr-java-0112
- **Changes**:
  - Replaced all local file system operations with Azure Blob Storage
  - Created `AzureBlobStorageService` for cloud-native file operations
  - Updated `IOUtils` to use Azure Blob Storage instead of local temp files
  - Modified `AvailabilityCheckerServlet` to export files to Azure Blob Storage

### 2. **Resource Management → Try-with-Resources**
- **Blocker Fixed**: cr-java-0098
- **Changes**:
  - Implemented try-with-resources for all AutoCloseable resources
  - Ensures automatic closure of database connections, file handles, and streams
  - Prevents resource exhaustion in containerized environments

### 3. **Secrets Management → Azure Key Vault**
- **Blocker Fixed**: cr-java-0113
- **Changes**:
  - Externalized all secrets to environment variables
  - Weather API key now loaded from Azure Key Vault via environment variable
  - Removed hard-coded credentials from source code

### 4. **EJB 2.x → Spring Boot Microservices**
- **Blocker Fixed**: cr-java-0085
- **Changes**:
  - Migrated `ModResortsCustomerInformation` from EJB Singleton to Spring Service
  - Replaced EJB container dependencies with Spring Boot
  - Implemented HikariCP connection pooling for database access

### 5. **Timer Dependencies → Azure Service Bus**
- **Blocker Fixed**: cr-java-0111
- **Changes**:
  - Created `AzureServiceBusSchedulerService` for distributed task scheduling
  - Replaced java.util.Timer with Azure Service Bus scheduled messages
  - Ensures timezone-agnostic scheduling across distributed environments

### 6. **WebSphere Dependencies → Spring Session + Azure Redis**
- **Blocker Fixed**: cr-java-0116
- **Changes**:
  - Removed WebSphere-specific clustering dependencies
  - Implemented Spring Session with Azure Cache for Redis
  - Updated `LogoutServlet` to use standard session management
  - Replaced `ResponseUtils` with Spring's `HtmlUtils` in `UpperServlet`

### 7. **WAR Packaging → Executable JAR**
- **Blocker Fixed**: cr-java-0107
- **Changes**:
  - Converted from WAR to executable JAR packaging
  - Embedded Tomcat server for self-contained deployment
  - Simplified containerization and cloud deployment

## Architecture Changes

### Before (Traditional Java EE)
```
WAR → External App Server (WebSphere) → Local File System → Direct JDBC
```

### After (Cloud-Native Spring Boot)
```
Executable JAR → Embedded Tomcat → Azure Blob Storage → HikariCP → Azure PostgreSQL
                                  → Azure Key Vault
                                  → Azure Cache for Redis
                                  → Azure Service Bus
```

## Configuration

### Environment Variables Required

#### Azure Storage
- `AZURE_STORAGE_ACCOUNT_URL`: Azure Storage account URL
- `AZURE_STORAGE_CONNECTION_STRING`: Connection string (or use Managed Identity)
- `AZURE_STORAGE_CONTAINER_NAME`: Container name for file storage

#### Azure Key Vault
- `AZURE_KEYVAULT_URI`: Azure Key Vault URI
- `WEATHER_API_KEY`: Weather API key (stored in Key Vault)

#### Azure Redis Cache
- `AZURE_REDIS_HOST`: Redis cache hostname
- `AZURE_REDIS_PORT`: Redis cache port (default: 6380)
- `AZURE_REDIS_PASSWORD`: Redis cache password

#### Azure Service Bus
- `AZURE_SERVICEBUS_CONNECTION_STRING`: Service Bus connection string
- `AZURE_SERVICEBUS_QUEUE_NAME`: Queue name for scheduled tasks

#### Database (Azure PostgreSQL)
- `DATABASE_URL`: JDBC connection URL
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password

#### Application
- `PORT`: Server port (default: 8080)

## Deployment Options

### Azure App Service
```bash
mvn clean package
az webapp deploy --resource-group <rg> --name <app-name> --src-path target/modresorts-2.0.0.jar
```

### Azure Container Apps
```bash
mvn clean package
docker build -t modresorts:2.0.0 .
az containerapp create --name modresorts --resource-group <rg> --image modresorts:2.0.0
```

### Azure Kubernetes Service (AKS)
```bash
mvn clean package
docker build -t modresorts:2.0.0 .
kubectl apply -f k8s/deployment.yaml
```

## Running Locally

### Prerequisites
- Java 8 or higher
- Maven 3.6+
- Azure Storage Emulator or Azure Storage account
- Azure Redis Cache or local Redis instance

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar target/modresorts-2.0.0.jar
```

Or with Maven:
```bash
mvn spring-boot:run
```

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

## Monitoring and Observability

### Logging
- Structured logging configured for cloud environments
- JSON format for easy parsing by Azure Monitor
- Log levels configurable via environment variables

### Health Checks
Spring Boot Actuator endpoints available at:
- `/actuator/health` - Application health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

### Application Insights
Configure Azure Application Insights for distributed tracing:
```properties
azure.application-insights.instrumentation-key=${APPINSIGHTS_INSTRUMENTATIONKEY}
```

## Security Considerations

1. **Managed Identity**: Use Azure Managed Identity for authentication to Azure services
2. **Key Vault**: All secrets stored in Azure Key Vault
3. **Network Security**: Configure NSGs and private endpoints for Azure services
4. **SSL/TLS**: Enable HTTPS for all external communication
5. **Session Security**: Sessions stored in Azure Redis with encryption

## Performance Optimizations

1. **Connection Pooling**: HikariCP configured for optimal database performance
2. **Caching**: Azure Redis Cache for distributed caching
3. **Blob Storage**: Optimized for large file operations
4. **Horizontal Scaling**: Stateless design enables auto-scaling

## Troubleshooting

### Common Issues

1. **Azure Storage Connection Failed**
   - Verify `AZURE_STORAGE_CONNECTION_STRING` is set correctly
   - Check network connectivity to Azure Storage
   - Ensure Managed Identity has Storage Blob Data Contributor role

2. **Redis Connection Failed**
   - Verify Redis hostname and port
   - Check Redis password
   - Ensure SSL is enabled for Azure Redis Cache

3. **Database Connection Failed**
   - Verify database URL, username, and password
   - Check firewall rules for Azure PostgreSQL
   - Ensure HikariCP pool settings are appropriate

## Migration Notes

### Breaking Changes
- EJB annotations removed (replaced with Spring annotations)
- WebSphere-specific APIs removed
- Local file paths no longer supported (use Azure Blob Storage)
- Timer-based scheduling replaced with Azure Service Bus

### Backward Compatibility
- REST API endpoints remain unchanged
- Business logic preserved
- Data models unchanged

## Support

For issues or questions, contact the development team or refer to:
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Azure SDK for Java](https://docs.microsoft.com/en-us/azure/developer/java/)
- [Azure App Service Documentation](https://docs.microsoft.com/en-us/azure/app-service/)
