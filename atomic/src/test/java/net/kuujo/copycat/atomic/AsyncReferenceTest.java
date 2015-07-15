/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.atomic;

import net.jodah.concurrentunit.ConcurrentTestCase;
import net.kuujo.copycat.Copycat;
import net.kuujo.copycat.Node;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

/**
 * Async reference test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@Test
public class AsyncReferenceTest extends ConcurrentTestCase {

  /**
   * Tests setting and getting a value.
   */
  @SuppressWarnings("unchecked")
  public void testSetGet() throws Throwable {
    List<Copycat> copycats = createCopycats(3);

    Copycat copycat = copycats.get(0);

    Node node = copycat.create("/test").get();
    AsyncReference<String> reference = node.create(AsyncReference.class).get();

    expectResume();
    reference.set("Hello world!").thenRun(this::resume);
    await();

    expectResume();
    reference.get().thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });
    await();
  }

  /**
   * Tests compare-and-set.
   */
  public void testCompareAndSet() throws Throwable {
    List<Copycat> copycats = createCopycats(3);

    Node node1 = copycats.get(0).create("/test").get();
    AsyncReference<Integer> reference1 = node1.create(AsyncReference.class).get();

    expectResume();
    reference1.set(1).thenRun(this::resume);
    await();

    Node node2 = copycats.get(0).create("/test").get();
    AsyncReference<Integer> reference2 = node2.create(AsyncReference.class).get();

    expectResume();
    reference2.compareAndSet(1, 2).thenAccept(result -> {
      threadAssertTrue(result);
      resume();
    });
    await();

    expectResume();
    reference2.compareAndSet(1, 3).thenAccept(result -> {
      threadAssertFalse(result);
      resume();
    });
    await();
  }

  /**
   * Creates a Copycat instance.
   */
  private List<Copycat> createCopycats(int nodes) throws Throwable {
    /*TestMemberRegistry registry = new TestMemberRegistry();

    List<Copycat> copycats = new ArrayList<>();

    expectResumes(nodes);

    for (int i = 1; i <= nodes; i++) {
      TestCluster.Builder builder = TestCluster.builder()
        .withMemberId(i)
        .withRegistry(registry);

      for (int j = 1; j <= nodes; j++) {
        builder.addMember(TestMember.builder()
          .withId(j)
          .withAddress(String.format("test-%d", j))
          .build());
      }

      Copycat copycat = CopycatServer.builder()
        .withCluster(builder.build())
        .withLog(Log.builder()
          .withStorageLevel(StorageLevel.MEMORY)
          .build())
        .build();

      copycat.open().thenRun(this::resume);

      copycats.add(copycat);
    }*/

    await();

    return Collections.EMPTY_LIST;
  }

}
