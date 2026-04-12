/******************************************************************************

Copyright (c) 2026, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.test;

import org.arl.fjage.remote.AsyncExecutor;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AsyncExecutorTest {

  private static final long TIMEOUT = 200;
  private static final long LONG_DELAY = 500;

  @Test
  public void testAnyTrueReturnsTrueWhenAnyTaskMatches() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      boolean result = async.anyTrue(Arrays.asList(1, 2, 3), value -> {
        pause(value == 2 ? 20 : 120);
        return value == 2;
      }, TIMEOUT);

      assertTrue(result);
    }
  }

  @Test
  public void testAnyTrueReturnsFalseWhenNoTaskMatches() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      boolean result = async.anyTrue(Arrays.asList(1, 2, 3), value -> {
        if (value == 3) pause(LONG_DELAY);
        return false;
      }, TIMEOUT);

      assertFalse(result);
    }
  }

  @Test
  public void testAllTrueReturnsTrueWhenAllTasksMatch() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      boolean result = async.allTrue(Arrays.asList(1, 2, 3), value -> {
        pause(20L * value);
        return true;
      }, TIMEOUT);

      assertTrue(result);
    }
  }

  @Test
  public void testAllTrueReturnsFalseWhenAnyTaskFails() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      boolean result = async.allTrue(Arrays.asList(1, 2, 3), value -> {
        if (value == 2) throw new IllegalStateException("boom");
        return true;
      }, TIMEOUT);

      assertFalse(result);
    }
  }

  @Test
  public void testFirstSuccessfulReturnsFirstCompletedResult() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      String result = async.firstSuccessful(Arrays.asList(1, 2, 3), value -> {
        if (value == 1) throw new IllegalArgumentException("fail");
        pause(value == 2 ? 30 : 120);
        return "task-" + value;
      }, TIMEOUT);

      assertEquals("task-2", result);
    }
  }

  @Test(expected = RuntimeException.class, timeout = 2000)
  public void testFirstSuccessfulThrowsWhenAllTasksFailOrTimeout() {
    try (AsyncExecutor async = new AsyncExecutor(2)) {
      async.firstSuccessful(Arrays.asList(1, 2), value -> {
        if (value == 1) throw new IllegalArgumentException("fail");
        pause(LONG_DELAY);
        return "task-" + value;
      }, TIMEOUT);
    }
  }

  @Test
  public void testFirstMatchReturnsMatchingValue() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      Optional<String> result = async.firstMatch(Arrays.asList(1, 2, 3), value -> {
        pause(value == 3 ? 25 : 100);
        return value == 3 ? "match" : "skip-" + value;
      }, value -> value.startsWith("match"), TIMEOUT);

      assertTrue(result.isPresent());
      assertEquals("match", result.get());
    }
  }

  @Test
  public void testFirstMatchReturnsEmptyWhenNoTaskMatches() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      Optional<String> result = async.firstMatch(Arrays.asList(1, 2, 3), value -> {
        if (value == 3) pause(LONG_DELAY);
        return "skip-" + value;
      }, value -> value.startsWith("match"), TIMEOUT);

      assertFalse(result.isPresent());
    }
  }

  @Test
  public void testAllReturnsNullForFailedOrTimedOutTasks() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      List<String> result = async.all(Arrays.asList(1, 2, 3), value -> {
        if (value == 2) throw new IllegalArgumentException("fail");
        if (value == 3) pause(LONG_DELAY);
        return "task-" + value;
      }, TIMEOUT);

      assertEquals(Arrays.asList("task-1", null, null), result);
    }
  }

  @Test
  public void testConcatFlattensResultsAndSkipsNullChunks() {
    try (AsyncExecutor async = new AsyncExecutor(3)) {
      String[] result = async.concat(Arrays.asList(1, 2, 3), value -> {
        if (value == 2) return null;
        return new String[] {"task-" + value, "done-" + value};
      }, String[]::new, TIMEOUT);

      assertArrayEquals(new String[] {"task-1", "done-1", "task-3", "done-3"}, result);
    }
  }

  @Test
  public void testRunAllExecutesConsumerForEachInput() {
    try (AsyncExecutor async = new AsyncExecutor(4)) {
      AtomicInteger sum = new AtomicInteger();
      Set<Integer> seen = ConcurrentHashMap.newKeySet();

      async.runAll(Arrays.asList(1, 2, 3, 4), value -> {
        pause(20);
        sum.addAndGet(value);
        seen.add(value);
      }, TIMEOUT);

      assertEquals(10, sum.get());
      assertEquals(4, seen.size());
      assertTrue(seen.containsAll(Arrays.asList(1, 2, 3, 4)));
    }
  }

  private void pause(long delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}