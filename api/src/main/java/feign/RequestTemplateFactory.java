/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign;

import feign.codec.Encoder;

/**
 * Binds method arguments to a fresh request template using Feign's native binding rules.
 *
 * <p>This extension point lets alternative method handlers reuse argument expansion and encoding
 * without executing a {@link Client} or manufacturing a {@link Response}. In particular, streaming
 * transports can prepare requests on subscription without adapting their response bodies to a
 * blocking input stream.
 *
 * <p>The caller owns request interception and applying the target after binding, as in Feign's
 * standard method handlers. No HTTP request is sent by this factory.
 */
@Experimental
@FunctionalInterface
public interface RequestTemplateFactory {

  /**
   * Returns a new resolved template for the supplied invocation arguments.
   *
   * @param arguments the invocation arguments, in declaration order, as received by the method
   *     handler; must not be null, pass an empty array for methods without arguments
   * @return an independent template with the method's parameters, headers and body resolved
   */
  RequestTemplate create(Object[] arguments);

  /**
   * Creates a reusable argument binder for contract metadata and the supplied request encoders.
   *
   * <p>Each invocation creates an independent template. Encoders and parameter expanders have the
   * same concurrency requirements as when used with a standard Feign client.
   *
   * @param target the API target
   * @param metadata method metadata parsed by a contract
   * @param encoder request body encoder
   * @param queryMapEncoder query map encoder
   */
  static RequestTemplateFactory forMethod(
      Target<?> target, MethodMetadata metadata, Encoder encoder, QueryMapEncoder queryMapEncoder) {
    Util.checkNotNull(target, "target");
    Util.checkNotNull(metadata, "metadata");
    RequestTemplate.Factory delegate =
        new RequestTemplateFactoryResolver(encoder, queryMapEncoder).resolve(target, metadata);
    return delegate::create;
  }
}
