package io.atomix.primitive.proxy;

import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.service.ServiceType;
import io.atomix.utils.concurrent.ThreadContext;

/**
 * Primitive proxy.
 */
public interface PrimitiveProxy {

  /**
   * Returns the primitive name.
   *
   * @return the primitive name
   */
  String name();

  /**
   * Returns the service type.
   *
   * @return the service type
   */
  ServiceType type();

  /**
   * Returns the partition ID.
   *
   * @return the partition ID
   */
  PartitionId partitionId();

  /**
   * Returns the proxy thread context.
   *
   * @return the proxy thread context
   */
  ThreadContext context();

  /**
   * Proxy context.
   */
  class Context {
    private final String name;
    private final ServiceType type;
    private final PartitionId partitionId;
    private final PrimitiveManagementService managementService;

    public Context(
        String name,
        ServiceType type,
        PartitionId partitionId,
        PrimitiveManagementService managementService) {
      this.name = name;
      this.type = type;
      this.partitionId = partitionId;
      this.managementService = managementService;
    }

    /**
     * Returns the primitive name.
     *
     * @return the primitive name
     */
    public String name() {
      return name;
    }

    /**
     * Returns the service type.
     *
     * @return the service type
     */
    public ServiceType type() {
      return type;
    }

    /**
     * Returns the partition ID.
     *
     * @return the partition ID
     */
    public PartitionId partitionId() {
      return partitionId;
    }

    /**
     * Returns the primitive management service.
     *
     * @return the primitive management service
     */
    public PrimitiveManagementService managementService() {
      return managementService;
    }
  }
}
