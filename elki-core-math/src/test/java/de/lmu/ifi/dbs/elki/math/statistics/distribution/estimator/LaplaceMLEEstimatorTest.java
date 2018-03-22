/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2018
 * ELKI Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.lmu.ifi.dbs.elki.math.statistics.distribution.estimator;

import org.junit.Test;

import de.lmu.ifi.dbs.elki.math.statistics.distribution.LaplaceDistribution;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.DoubleArrayAdapter;

/**
 * Regression test the estimation for the Laplace MLE estimation.
 * 
 * @author Erich Schubert
 */
public class LaplaceMLEEstimatorTest extends AbstractDistributionEstimatorTest {
  @Test
  public void testEstimator() {
    final LaplaceMLEEstimator est = instantiate(LaplaceMLEEstimator.class, LaplaceDistribution.class);
    load("lap.ascii.gz");
    double[] data;
    LaplaceDistribution dist;
    data = this.data.get("random_1_3");
    dist = est.estimate(data, DoubleArrayAdapter.STATIC);
    assertStat("rate", dist.getRate(), 1, 0.06237963500909771);
    assertStat("loc", dist.getLocation(), 3, -0.04027330298719978);
    data = this.data.get("random_4_05");
    dist = est.estimate(data, DoubleArrayAdapter.STATIC);
    assertStat("rate", dist.getRate(), 4, 0.03272774696140335);
    assertStat("loc", dist.getLocation(), .5, -0.045715312384149276);
  }
}
