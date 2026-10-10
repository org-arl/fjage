package org.arl.fjage;

import static org.junit.Assert.*;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.Test;

public class AgentLocalRandomTest {
  @Test
  public void gaussianUsesStandardDeviationAndVarianceHelperKeepsLegacySequence() {
    AgentLocalRandom random = new AgentLocalRandom();
    Random expected = new Random(42);
    random.setSeed(42);
    RandomGenerator generator = random;
    assertEquals(expected.nextGaussian(3, 4), generator.nextGaussian(3, 4), 0);
    assertEquals(expected.nextGaussian()*2+3, random.nextGaussianVariance(3, 4), 0);
    assertThrows(IllegalArgumentException.class, () -> generator.nextGaussian(0, -1));
  }

  @Test
  public void boundedDoublesValidateBoundsAndStayFiniteAndExclusive() {
    AgentLocalRandom random = new AgentLocalRandom();
    random.setSeed(42);
    for (double[] bounds : new double[][] {{1, 1}, {2, 1}, {Double.NaN, 1}, {0, Double.POSITIVE_INFINITY}, {-Double.MAX_VALUE, Double.MAX_VALUE}})
      assertThrows(IllegalArgumentException.class, () -> random.nextDouble(bounds[0], bounds[1]));
    for (int i = 0; i < 1000; i++) {
      double value = random.nextDouble(-Double.MAX_VALUE/4, Double.MAX_VALUE/4);
      assertTrue(Double.isFinite(value));
      assertTrue(value >= -Double.MAX_VALUE/4 && value < Double.MAX_VALUE/4);
      assertEquals(1, random.nextDouble(1, Math.nextUp(1.0)), 0);
    }
  }
  @Test
  public void namedGeneratorsAreRepeatableAndAdvanceOnlyTheirSeedSource() {
    AgentLocalRandom first = new AgentLocalRandom();
    AgentLocalRandom second = new AgentLocalRandom();
    first.setSeed(42);
    second.setSeed(42);
    RandomGenerator a = first.createGenerator("L64X128MixRandom");
    RandomGenerator b = second.createGenerator("L64X128MixRandom");
    for (int i = 0; i < 10; i++) assertEquals(a.nextExponential(), b.nextExponential(), 0);
    Random expected = new Random(42);
    expected.nextLong();
    assertEquals(expected.nextDouble(), first.nextDouble(), 0);
    first.setSeed(42);
    assertThrows(IllegalArgumentException.class, () -> first.createGenerator("SecureRandom"));
    assertThrows(IllegalArgumentException.class, () -> first.createGenerator("unknown-algorithm"));
    assertEquals(new Random(42).nextLong(), first.nextLong());
  }

}
