package org.arl.fjage.remote;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;


/**
 * A helper class to execute multiple tasks in parallel with timeouts and various completion strategies.
 * <p>
 * This class provides methods to execute a collection of tasks in parallel and retrieve results based on different
 * criteria (e.g., any true, first successful, first match, all results, concatenation). Each task is executed with a specified timeout.
 * <p>
 *  Example usage:
 * <pre>
 *   try (AsyncExecutor async = new AsyncExecutor(50)) {
 *
 *     boolean ok = async.anyTrue(tasks, this::check, 2000);
 *
 *     Result r = async.firstSuccessful(tasks, this::fetch, 2000);
 *
 *     Optional<Result> r2 =
 *         async.firstMatch(tasks, this::fetch, this::isGood, 2000);
 *
 *     List<Result> all =
 *         async.all(tasks, this::fetch, 2000);
 *
 *     String[] combined =
 *         async.concat(tasks, this::fetchChunk, String[]::new, 2000);
 * }
 * </pre>
 */
public class AsyncExecutor implements AutoCloseable {

  private final ExecutorService executor;
  private final ScheduledExecutorService scheduler;

  public AsyncExecutor(int threads) {
    this.executor = Executors.newFixedThreadPool(threads);
    this.scheduler = Executors.newScheduledThreadPool(1);
  }

  private <T> CompletableFuture<T> withTimeout(
      CompletableFuture<T> f,
      long timeoutMs) {

    CompletableFuture<T> timeoutFuture = new CompletableFuture<>();

    ScheduledFuture<?> scheduledTask = scheduler.schedule(
        () -> timeoutFuture.completeExceptionally(new TimeoutException()),
        timeoutMs,
        TimeUnit.MILLISECONDS
    );

    return f.applyToEither(timeoutFuture, Function.identity())
        .whenComplete((result, ex) -> scheduledTask.cancel(false));
  }

  private <T, R> List<CompletableFuture<R>> map(
      Collection<T> input,
      Function<T, R> fn,
      long timeoutMs) {

    List<CompletableFuture<R>> futures = new ArrayList<>(input.size());

    for (T t : input) {
      CompletableFuture<R> f =
          CompletableFuture.supplyAsync(() -> fn.apply(t), executor);

      futures.add(withTimeout(f, timeoutMs));
    }

    return futures;
  }

  public <T> boolean anyTrue(
      Collection<T> input,
      Function<T, Boolean> fn,
      long timeoutMs) {

    List<CompletableFuture<Boolean>> futures = map(input, fn, timeoutMs);

    CompletableFuture<Boolean> result = new CompletableFuture<>();

    for (CompletableFuture<Boolean> f : futures) {
      f.thenAccept(r -> {
        if (Boolean.TRUE.equals(r)) {
          result.complete(true);
        }
      }).exceptionally(ex -> null);
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
        .handle((ignored, ex) -> {
          result.complete(false);
          return null;
        });

    boolean value = result.join();
    futures.forEach(f -> f.cancel(true));
    return value;
  }

  public <T> boolean allTrue(
      Collection<T> input,
      Function<T, Boolean> fn,
      long timeoutMs) {

    return all(input, fn, timeoutMs).stream().allMatch(Boolean.TRUE::equals);
  }

  public <T> void runAll(
      Collection<T> input,
      Consumer<T> fn,
      long timeoutMs) {

    all(input, t -> {
      fn.accept(t);
      return null;
    }, timeoutMs);
  }

  public <T, R> R firstSuccessful(
      Collection<T> input,
      Function<T, R> fn,
      long timeoutMs) {

    if (input.isEmpty()) return null;

    List<CompletableFuture<R>> futures = map(input, fn, timeoutMs);

    CompletableFuture<R> result = new CompletableFuture<>();
    AtomicInteger remaining = new AtomicInteger(futures.size());

    for (CompletableFuture<R> f : futures) {
      // only accept successful completion
      f.thenAccept(result::complete).exceptionally(ex -> {
        // if this was the last one and all failed → fail overall
        if (remaining.decrementAndGet() == 0) {
          result.completeExceptionally(
              new RuntimeException("All tasks failed or timed out", ex));
        }
        return null;
      });
    }

    try {
      return result.join();
    } finally {
      futures.forEach(f -> f.cancel(true));
    }
  }

  public <T, R> Optional<R> firstMatch(
      Collection<T> input,
      Function<T, R> fn,
      Predicate<R> predicate,
      long timeoutMs) {

    List<CompletableFuture<R>> futures = map(input, fn, timeoutMs);

    CompletableFuture<R> result = new CompletableFuture<>();

    for (CompletableFuture<R> f : futures) {
      f.thenAccept(r -> {
        if (predicate.test(r)) {
          result.complete(r);
        }
      }).exceptionally(ex -> null);
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
        .handle((ignored, ex) -> {
          result.complete(null);
          return null;
        });

    R r = result.join();
    futures.forEach(f -> f.cancel(true));
    return Optional.ofNullable(r);
  }

  // ---- 4. all ----
  public <T, R> List<R> all(
      Collection<T> input,
      Function<T, R> fn,
      long timeoutMs) {

    List<CompletableFuture<R>> futures = map(input, fn, timeoutMs);

    List<R> results = new ArrayList<>(futures.size());

    for (CompletableFuture<R> f : futures) {
      try {
        results.add(f.join());
      } catch (CompletionException e) {
        results.add(null);
      }
    }

    return results;
  }

  // ---- 5. concat ----
  public <T, R> R[] concat(
      Collection<T> input,
      Function<T, R[]> fn,
      IntFunction<R[]> factory,
      long timeoutMs) {

    List<R[]> parts = all(input, fn, timeoutMs);

    int total = 0;
    for (R[] arr : parts) {
      if (arr != null) total += arr.length;
    }

    R[] result = factory.apply(total);

    int pos = 0;
    for (R[] arr : parts) {
      if (arr != null) {
        System.arraycopy(arr, 0, result, pos, arr.length);
        pos += arr.length;
      }
    }

    return result;
  }

  // ---- cleanup ----
  @Override
  public void close() {
    executor.shutdownNow();
    scheduler.shutdownNow();
  }
}