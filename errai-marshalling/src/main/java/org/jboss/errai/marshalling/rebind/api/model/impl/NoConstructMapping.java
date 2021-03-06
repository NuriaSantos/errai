/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.marshalling.rebind.api.model.impl;

import java.util.List;

import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaConstructor;
import org.jboss.errai.marshalling.rebind.api.model.ConstructorMapping;
import org.jboss.errai.marshalling.rebind.api.model.Mapping;

/**
 * @author Mike Brock
 */
public class NoConstructMapping implements ConstructorMapping {
  @Override
  public Mapping[] getMappings() {
    return new Mapping[0];
  }

  @Override
  public Class<?>[] getSignature() {
    return new Class<?>[0];
  }

  @Override
  public MetaConstructor getMember() {
    return null;
  }

  @Override
  public void setMappingClass(MetaClass clazz) {
  }

  @Override
  public MetaClass getMappingClass() {
    return null;
  }

  @Override
  public boolean isNoConstruct() {
    return true;
  }

  @Override
  public Mapping[] getMappingsInKeyOrder(List<String> keys) {
    return new Mapping[0];
  }
  
  @Override
  public int getIndex(String key) {
    return 0;
  }

}
