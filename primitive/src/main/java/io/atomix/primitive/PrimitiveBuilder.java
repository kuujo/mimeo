/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.PrimitiveProtocolConfig;
import io.atomix.primitive.protocol.ProxyProtocol;
import io.atomix.primitive.proxy.PrimitiveProxy;
import io.atomix.primitive.service.ServiceType;
import io.atomix.utils.Builder;
import io.atomix.utils.config.ConfigurationException;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.NamespaceConfig;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract builder for distributed primitives.
 *
 * @param <B> builder type
 * @param <P> primitive type
 */
public abstract class PrimitiveBuilder<B extends PrimitiveBuilder<B, C, P>, C extends PrimitiveConfig, P extends SyncPrimitive> implements Builder<P> {
  protected final PrimitiveType type;
  protected final String name;
  protected final C config;
  protected PrimitiveProtocol protocol;
  protected Serializer serializer;
  protected final PrimitiveManagementService managementService;

  public PrimitiveBuilder(PrimitiveType type, String name, C config, PrimitiveManagementService managementService) {
    this.type = checkNotNull(type, "type cannot be null");
    this.name = checkNotNull(name, "name cannot be null");
    this.config = checkNotNull(config, "config cannot be null");
    this.managementService = checkNotNull(managementService, "managementService cannot be null");
  }

  /**
   * Sets the primitive protocol.
   *
   * @param protocol the primitive protocol
   * @return the primitive builder
   */
  @SuppressWarnings("unchecked")
  protected B withProtocol(PrimitiveProtocol protocol) {
    this.protocol = protocol;
    return (B) this;
  }

  /**
   * Sets the primitive serializer.
   *
   * @param serializer the primitive serializer
   * @return the primitive builder
   */
  @SuppressWarnings("unchecked")
  public B withSerializer(Serializer serializer) {
    this.serializer = checkNotNull(serializer);
    return (B) this;
  }

  /**
   * Sets the primitive to read-only.
   *
   * @return the primitive builder
   */
  @SuppressWarnings("unchecked")
  public B withReadOnly() {
    config.setReadOnly();
    return (B) this;
  }

  /**
   * Sets whether the primitive is read-only.
   *
   * @param readOnly whether the primitive is read-only
   * @return the primitive builder
   */
  @SuppressWarnings("unchecked")
  public B withReadOnly(boolean readOnly) {
    config.setReadOnly(readOnly);
    return (B) this;
  }

  /**
   * Returns the primitive protocol.
   *
   * @return the primitive protocol
   */
  protected PrimitiveProtocol protocol() {
    PrimitiveProtocol protocol = this.protocol;
    if (protocol == null) {
      PrimitiveProtocolConfig<?> protocolConfig = config.getProtocolConfig();
      if (protocolConfig == null) {
        Collection<PartitionGroup> partitionGroups = managementService.getPartitionService().getPartitionGroups();
        if (partitionGroups.size() == 1) {
          protocol = partitionGroups.iterator().next().newProtocol();
        } else {
          String groups = Joiner.on(", ").join(partitionGroups.stream()
              .map(group -> group.name())
              .collect(Collectors.toList()));
          throw new ConfigurationException(String.format("Primitive protocol is ambiguous: %d partition groups found (%s)", partitionGroups.size(), groups));
        }
      } else {
        protocol = protocolConfig.getType().newProtocol(protocolConfig);
      }
    }
    return protocol;
  }

  /**
   * Returns the protocol serializer.
   *
   * @return the protocol serializer
   */
  protected Serializer serializer() {
    Serializer serializer = this.serializer;
    if (serializer == null) {
      synchronized (this) {
        serializer = this.serializer;
        if (serializer == null) {
          NamespaceConfig config = this.config.getNamespaceConfig();
          if (config == null) {
            serializer = Serializer.using(Namespaces.BASIC);
          } else {
            serializer = Serializer.using(Namespace.builder()
                .register(Namespaces.BASIC)
                .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
                .register(new Namespace(config))
                .build());
          }
          this.serializer = serializer;
        }
      }
    }
    return serializer;
  }

  /**
   * Creates a new map of proxies for all partitions for the configured protocol.
   *
   * @param factory the proxy factory
   * @param <P>     the proxy type
   * @return the proxy
   */
  protected <P extends PrimitiveProxy> Map<PartitionId, P> newMultitonProxies(
      ServiceType serviceType, Function<PrimitiveProxy.Context, P> factory) {
    List<PartitionId> partitions = managementService.getPartitionService()
        .getPartitionGroup((ProxyProtocol) protocol())
        .getPartitions()
        .stream()
        .map(Partition::id)
        .collect(Collectors.toList());
    return newProxies(serviceType, partitions, factory);
  }

  /**
   * Creates a new map of proxies for the given partitions.
   *
   * @param partitions the partitions for which to create the proxies
   * @param factory    the proxy factory
   * @param <P>        the proxy type
   * @return the proxy
   */
  protected <P extends PrimitiveProxy> Map<PartitionId, P> newProxies(
      ServiceType serviceType, List<PartitionId> partitions, Function<PrimitiveProxy.Context, P> factory) {
    return partitions.stream()
        .map(partitionId -> newProxy(serviceType, partitionId, factory))
        .collect(Collectors.toMap(PrimitiveProxy::partitionId, Function.identity()));
  }

  /**
   * Creates a new singleton primitive proxy.
   *
   * @param factory the proxy factory
   * @param <P>     the proxy type
   * @return the proxy
   */
  protected <P extends PrimitiveProxy> P newSingletonProxy(ServiceType serviceType, Function<PrimitiveProxy.Context, P> factory) {
    PartitionId partitionId = managementService.getPartitionService()
        .getPartitionGroup((ProxyProtocol) protocol())
        .getPartition(name)
        .id();
    return newProxy(serviceType, partitionId, factory);
  }

  /**
   * Creates a new primitive proxy.
   *
   * @param partitionId the partition for which to return the proxy
   * @param factory     the proxy factory
   * @param <P>         the proxy type
   * @return the proxy
   */
  protected <P extends PrimitiveProxy> P newProxy(ServiceType serviceType, PartitionId partitionId, Function<PrimitiveProxy.Context, P> factory) {
    return factory.apply(new PrimitiveProxy.Context(name, serviceType, partitionId, managementService));
  }

  /**
   * Builds a new instance of the primitive.
   * <p>
   * The returned instance will be distinct from all other instances of the same primitive on this node, with a
   * distinct session, ordering guarantees, memory, etc.
   *
   * @return a new instance of the primitive
   */
  @Override
  public P build() {
    try {
      return buildAsync().join();
    } catch (Exception e) {
      if (e instanceof CompletionException && e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      } else {
        throw e;
      }
    }
  }

  /**
   * Builds a new instance of the primitive asynchronously.
   * <p>
   * The returned instance will be distinct from all other instances of the same primitive on this node, with a
   * distinct session, ordering guarantees, memory, etc.
   *
   * @return asynchronous distributed primitive
   */
  public abstract CompletableFuture<P> buildAsync();

  /**
   * Gets or builds a singleton instance of the primitive.
   * <p>
   * The returned primitive will be shared by all {@code get()} calls for the named primitive. If no instance has yet
   * been constructed, the instance will be built from this builder's configuration.
   *
   * @return a singleton instance of the primitive
   */
  public P get() {
    try {
      return getAsync().join();
    } catch (Exception e) {
      if (e instanceof CompletionException && e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      } else {
        throw e;
      }
    }
  }

  /**
   * Gets or builds a singleton instance of the primitive asynchronously.
   * <p>
   * The returned primitive will be shared by all {@code get()} calls for the named primitive. If no instance has yet
   * been constructed, the instance will be built from this builder's configuration.
   *
   * @return a singleton instance of the primitive
   */
  public CompletableFuture<P> getAsync() {
    return managementService.getPrimitiveCache().getPrimitive(name, this::buildAsync);
  }
}
