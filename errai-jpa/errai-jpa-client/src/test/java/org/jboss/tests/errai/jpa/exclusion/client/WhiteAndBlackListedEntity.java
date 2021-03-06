package org.jboss.tests.errai.jpa.exclusion.client;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import org.jboss.errai.ioc.client.api.TestOnly;

/**
 * @author Divya Dadlani <ddadlani@redhat.com>
 */
@TestOnly @Entity
public class WhiteAndBlackListedEntity {

  @GeneratedValue
  @Id
  public long id;

  public WhiteAndBlackListedEntity() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }
}
