/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2017
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
package de.lmu.ifi.dbs.elki.algorithm.clustering.subspace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.subspace.clique.CLIQUEInterval;
import de.lmu.ifi.dbs.elki.algorithm.clustering.subspace.clique.CLIQUESubspace;
import de.lmu.ifi.dbs.elki.algorithm.clustering.subspace.clique.CLIQUEUnit;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.Subspace;
import de.lmu.ifi.dbs.elki.data.model.SubspaceModel;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.StepProgress;
import de.lmu.ifi.dbs.elki.math.MathUtil;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Centroid;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.io.FormatUtil;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;
import net.jafama.FastMath;

/**
 * Implementation of the CLIQUE algorithm, a grid-based algorithm to identify
 * dense clusters in subspaces of maximum dimensionality.
 * 
 * The implementation consists of two steps: <br>
 * 1. Identification of subspaces that contain clusters <br>
 * 2. Identification of clusters
 * 
 * The third step of the original algorithm (Generation of minimal description
 * for the clusters) is not (yet) implemented.
 * 
 * Reference:
 * <p>
 * R. Agrawal, J. Gehrke, D. Gunopulos, P. Raghavan:<br>
 * Automatic Subspace Clustering of High Dimensional Data for Data Mining
 * Applications. <br>
 * In Proc. ACM SIGMOD Int. Conf. on Management of Data, Seattle, WA, 1998.
 * </p>
 * 
 * @author Elke Achtert
 * @since 0.2
 * 
 * @apiviz.has SubspaceModel
 * @apiviz.has CLIQUESubspace
 * @apiviz.uses CLIQUEUnit
 * 
 * @param <V> the type of NumberVector handled by this Algorithm
 */
@Title("CLIQUE: Automatic Subspace Clustering of High Dimensional Data for Data Mining Applications")
@Description("Grid-based algorithm to identify dense clusters in subspaces of maximum dimensionality.")
@Reference(authors = "R. Agrawal, J. Gehrke, D. Gunopulos, P. Raghavan", //
    title = "Automatic Subspace Clustering of High Dimensional Data for Data Mining Applications", //
    booktitle = "Proc. SIGMOD Conference, Seattle, WA, 1998", //
    url = "https://doi.org/10.1145/276304.276314")
public class CLIQUE<V extends NumberVector> extends AbstractAlgorithm<Clustering<SubspaceModel>> implements SubspaceClusteringAlgorithm<SubspaceModel> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(CLIQUE.class);

  /**
   * Holds the value of {@link Parameterizer#XSI_ID}.
   */
  private int xsi;

  /**
   * Holds the value of {@link Parameterizer#TAU_ID}.
   */
  private double tau;

  /**
   * Holds the value of {@link Parameterizer#PRUNE_ID}.
   */
  private boolean prune;

  /**
   * Constructor.
   * 
   * @param xsi Xsi value
   * @param tau Tau value
   * @param prune Prune flag
   */
  public CLIQUE(int xsi, double tau, boolean prune) {
    super();
    this.xsi = xsi;
    this.tau = tau;
    this.prune = prune;
  }

  /**
   * Performs the CLIQUE algorithm on the given database.
   * 
   * @param relation Data relation to process
   * @return Clustering result
   */
  public Clustering<SubspaceModel> run(Relation<V> relation) {
    final int dimensionality = RelationUtil.dimensionality(relation);
    StepProgress step = new StepProgress(2);

    // 1. Identification of subspaces that contain clusters
    step.beginStep(1, "Identification of subspaces that contain clusters", LOG);
    ArrayList<List<CLIQUESubspace<V>>> dimensionToDenseSubspaces = new ArrayList<>(dimensionality);
    List<CLIQUESubspace<V>> denseSubspaces = findOneDimensionalDenseSubspaces(relation);
    dimensionToDenseSubspaces.add(denseSubspaces);
    if(LOG.isVerbose()) {
      LOG.verbose("1-dimensional dense subspaces: " + denseSubspaces.size());
    }
    if(LOG.isDebugging()) {
      for(CLIQUESubspace<V> s : denseSubspaces) {
        LOG.debug(s.toString(" "));
      }
    }

    for(int k = 2; k <= dimensionality && !denseSubspaces.isEmpty(); k++) {
      denseSubspaces = findDenseSubspaces(relation, denseSubspaces);
      assert (dimensionToDenseSubspaces.size() == k - 1);
      dimensionToDenseSubspaces.add(denseSubspaces);
      if(LOG.isVerbose()) {
        LOG.verbose(k + "-dimensional dense subspaces: " + denseSubspaces.size());
      }
      if(LOG.isDebugging()) {
        for(CLIQUESubspace<V> s : denseSubspaces) {
          LOG.debug(s.toString(" "));
        }
      }
    }

    // 2. Identification of clusters
    step.beginStep(2, "Identification of clusters", LOG);
    // build result
    Clustering<SubspaceModel> result = new Clustering<>("CLIQUE clustering", "clique-clustering");
    for(int dim = 0; dim < dimensionToDenseSubspaces.size(); dim++) {
      List<CLIQUESubspace<V>> subspaces = dimensionToDenseSubspaces.get(dim);
      List<Pair<Subspace, ModifiableDBIDs>> modelsAndClusters = determineClusters(subspaces);

      if(LOG.isVerbose()) {
        LOG.verbose((dim + 1) + "-dimensional clusters: " + modelsAndClusters.size());
      }

      for(Pair<Subspace, ModifiableDBIDs> modelAndCluster : modelsAndClusters) {
        Cluster<SubspaceModel> newCluster = new Cluster<>(modelAndCluster.second);
        newCluster.setModel(new SubspaceModel(modelAndCluster.first, Centroid.make(relation, modelAndCluster.second).getArrayRef()));
        result.addToplevelCluster(newCluster);
      }
    }

    return result;
  }

  /**
   * Determines the clusters in the specified dense subspaces.
   * 
   * @param denseSubspaces the dense subspaces in reverse order by their
   *        coverage
   * @return the clusters in the specified dense subspaces and the corresponding
   *         cluster models
   */
  private List<Pair<Subspace, ModifiableDBIDs>> determineClusters(List<CLIQUESubspace<V>> denseSubspaces) {
    List<Pair<Subspace, ModifiableDBIDs>> clusters = new ArrayList<>();

    for(CLIQUESubspace<V> subspace : denseSubspaces) {
      List<Pair<Subspace, ModifiableDBIDs>> clustersInSubspace = subspace.determineClusters();
      if(LOG.isDebugging()) {
        LOG.debugFine("Subspace " + subspace + " clusters " + clustersInSubspace.size());
      }
      clusters.addAll(clustersInSubspace);
    }
    return clusters;
  }

  /**
   * Determines the one dimensional dense subspaces and performs a pruning if
   * this option is chosen.
   * 
   * @param database the database to run the algorithm on
   * @return the one dimensional dense subspaces reverse ordered by their
   *         coverage
   */
  private List<CLIQUESubspace<V>> findOneDimensionalDenseSubspaces(Relation<V> database) {
    List<CLIQUESubspace<V>> denseSubspaceCandidates = findOneDimensionalDenseSubspaceCandidates(database);
    return prune ? pruneDenseSubspaces(denseSubspaceCandidates) : denseSubspaceCandidates;
  }

  /**
   * Determines the {@code k}-dimensional dense subspaces and performs a pruning
   * if this option is chosen.
   * 
   * @param database the database to run the algorithm on
   * @param denseSubspaces the {@code (k-1)}-dimensional dense subspaces
   * @return a list of the {@code k}-dimensional dense subspaces sorted in
   *         reverse order by their coverage
   */
  private List<CLIQUESubspace<V>> findDenseSubspaces(Relation<V> database, List<CLIQUESubspace<V>> denseSubspaces) {
    List<CLIQUESubspace<V>> denseSubspaceCandidates = findDenseSubspaceCandidates(database, denseSubspaces);
    return prune ? pruneDenseSubspaces(denseSubspaceCandidates) : denseSubspaceCandidates;
  }

  /**
   * Initializes and returns the one dimensional units.
   * 
   * @param database the database to run the algorithm on
   * @return the created one dimensional units
   */
  private Collection<CLIQUEUnit<V>> initOneDimensionalUnits(Relation<V> database) {
    StringBuilder buf = LOG.isDebuggingFiner() ? new StringBuilder(1000) : null;
    int dimensionality = RelationUtil.dimensionality(database);
    // initialize minima and maxima
    double[] minima = new double[dimensionality];
    double[] maxima = new double[dimensionality];
    Arrays.fill(minima, Double.MAX_VALUE);
    Arrays.fill(maxima, -Double.MAX_VALUE);
    // update minima and maxima
    for(DBIDIter it = database.iterDBIDs(); it.valid(); it.advance()) {
      updateMinMax(database.get(it), minima, maxima);
    }
    for(int i = 0; i < maxima.length; i++) {
      maxima[i] += 0.0001;
    }

    // determine the unit length in each dimension
    double[] unit_lengths = new double[dimensionality];
    for(int d = 0; d < dimensionality; d++) {
      unit_lengths[d] = (maxima[d] - minima[d]) / xsi;
    }

    if(buf != null) {
      FormatUtil.formatTo(buf.append("   minima: "), minima, ", ", FormatUtil.NF2);
      FormatUtil.formatTo(buf.append("\n   maxima: "), maxima, ", ", FormatUtil.NF2);
      FormatUtil.formatTo(buf.append("\n   unit lengths: "), unit_lengths, ", ", FormatUtil.NF2);
    }

    // determine the boundaries of the units
    double[][] unit_bounds = new double[xsi + 1][dimensionality];
    for(int x = 0; x <= xsi; x++) {
      for(int d = 0; d < dimensionality; d++) {
        unit_bounds[x][d] = (x < xsi) ? minima[d] + x * unit_lengths[d] : maxima[d];
      }
    }
    if(buf != null) {
      FormatUtil.formatTo(buf.append("   unit bounds "), unit_bounds, "    [", "]\n", ", ", FormatUtil.NF2);
    }

    // build the 1 dimensional units
    List<CLIQUEUnit<V>> units = new ArrayList<>(xsi * dimensionality);
    for(int x = 0; x < xsi; x++) {
      for(int d = 0; d < dimensionality; d++) {
        units.add(new CLIQUEUnit<V>(new CLIQUEInterval(d, unit_bounds[x][d], unit_bounds[x + 1][d])));
      }
    }

    if(buf != null) {
      LOG.debugFiner(buf.append("   total number of 1-dim units: ").append(units.size()).toString());
    }

    return units;
  }

  /**
   * Updates the minima and maxima array according to the specified feature
   * vector.
   * 
   * @param featureVector the feature vector
   * @param minima the array of minima
   * @param maxima the array of maxima
   */
  private void updateMinMax(V featureVector, double[] minima, double[] maxima) {
    assert (minima.length == featureVector.getDimensionality());
    for(int d = 0; d < featureVector.getDimensionality(); d++) {
      double v = featureVector.doubleValue(d);
      if(v == v) { // Avoid NaN.
        maxima[d] = MathUtil.max(v, maxima[d]);
        minima[d] = MathUtil.min(v, minima[d]);
      }
    }
  }

  /**
   * Determines the one-dimensional dense subspace candidates by making a pass
   * over the database.
   * 
   * @param database the database to run the algorithm on
   * @return the one-dimensional dense subspace candidates reverse ordered by
   *         their coverage
   */
  private List<CLIQUESubspace<V>> findOneDimensionalDenseSubspaceCandidates(Relation<V> database) {
    Collection<CLIQUEUnit<V>> units = initOneDimensionalUnits(database);
    // identify dense units
    double total = database.size();
    for(DBIDIter it = database.iterDBIDs(); it.valid(); it.advance()) {
      V featureVector = database.get(it);
      for(CLIQUEUnit<V> unit : units) {
        unit.addFeatureVector(it, featureVector);
      }
    }

    Collection<CLIQUEUnit<V>> denseUnits = new ArrayList<>();
    Map<Integer, CLIQUESubspace<V>> denseSubspaces = new HashMap<>();
    for(CLIQUEUnit<V> unit : units) {
      // unit is a dense unit
      if(unit.selectivity(total) >= tau) {
        denseUnits.add(unit);
        // add the dense unit to its subspace
        int dim = unit.getIntervals().iterator().next().getDimension();
        CLIQUESubspace<V> subspace_d = denseSubspaces.get(Integer.valueOf(dim));
        if(subspace_d == null) {
          denseSubspaces.put(Integer.valueOf(dim), subspace_d = new CLIQUESubspace<>(dim));
        }
        subspace_d.addDenseUnit(unit);
      }
    }

    if(LOG.isDebugging()) {
      LOG.debugFine(new StringBuilder().append("   number of 1-dim dense units: ").append(denseUnits.size()) //
          .append("\n   number of 1-dim dense subspace candidates: ").append(denseSubspaces.size()).toString());
    }

    List<CLIQUESubspace<V>> subspaceCandidates = new ArrayList<>(denseSubspaces.values());
    Collections.sort(subspaceCandidates, new CLIQUESubspace.CoverageComparator());
    return subspaceCandidates;
  }

  /**
   * Determines the {@code k}-dimensional dense subspace candidates from the
   * specified {@code (k-1)}-dimensional dense subspaces.
   * 
   * @param database the database to run the algorithm on
   * @param denseSubspaces the {@code (k-1)}-dimensional dense subspaces
   * @return a list of the {@code k}-dimensional dense subspace candidates
   *         reverse ordered by their coverage
   */
  private List<CLIQUESubspace<V>> findDenseSubspaceCandidates(Relation<V> database, List<CLIQUESubspace<V>> denseSubspaces) {
    // sort (k-1)-dimensional dense subspace according to their dimensions
    List<CLIQUESubspace<V>> denseSubspacesByDimensions = new ArrayList<>(denseSubspaces);
    Collections.sort(denseSubspacesByDimensions, new Subspace.DimensionComparator());

    // determine k-dimensional dense subspace candidates
    double all = database.size();
    List<CLIQUESubspace<V>> denseSubspaceCandidates = new ArrayList<>();

    while(!denseSubspacesByDimensions.isEmpty()) {
      CLIQUESubspace<V> s1 = denseSubspacesByDimensions.remove(0);
      for(CLIQUESubspace<V> s2 : denseSubspacesByDimensions) {
        CLIQUESubspace<V> s = s1.join(s2, all, tau);
        if(s != null) {
          denseSubspaceCandidates.add(s);
        }
      }
    }

    // sort reverse by coverage
    Collections.sort(denseSubspaceCandidates, new CLIQUESubspace.CoverageComparator());
    return denseSubspaceCandidates;
  }

  /**
   * Performs a MDL-based pruning of the specified dense subspaces as described
   * in the CLIQUE algorithm.
   * 
   * @param denseSubspaces the subspaces to be pruned sorted in reverse order by
   *        their coverage
   * @return the subspaces which are not pruned reverse ordered by their
   *         coverage
   */
  private List<CLIQUESubspace<V>> pruneDenseSubspaces(List<CLIQUESubspace<V>> denseSubspaces) {
    int[][] means = computeMeans(denseSubspaces);
    double[][] diffs = computeDiffs(denseSubspaces, means[0], means[1]);
    double[] codeLength = new double[denseSubspaces.size()];
    double minCL = Double.MAX_VALUE;
    int min_i = -1;

    for(int i = 0; i < denseSubspaces.size(); i++) {
      int mi = means[0][i], mp = means[1][i];
      double cl = codeLength[i] = log2OrZero(mi) + diffs[0][i] + log2OrZero(mp) + diffs[1][i];

      if(cl <= minCL) {
        minCL = cl;
        min_i = i;
      }
    }

    return denseSubspaces.subList(0, min_i + 1);
  }

  /**
   * The specified sorted list of dense subspaces is divided into the selected
   * set I and the pruned set P. For each set the mean of the cover fractions is
   * computed.
   * 
   * @param denseSubspaces the dense subspaces in reverse order by their
   *        coverage
   * @return the mean of the cover fractions, the first value is the mean of the
   *         selected set I, the second value is the mean of the pruned set P.
   */
  private int[][] computeMeans(List<CLIQUESubspace<V>> denseSubspaces) {
    int n = denseSubspaces.size() - 1;

    int[] mi = new int[n + 1], mp = new int[n + 1];
    double resultMI = 0, resultMP = 0;

    for(int i = 0; i < denseSubspaces.size(); i++) {
      resultMI += denseSubspaces.get(i).getCoverage();
      resultMP += denseSubspaces.get(n - i).getCoverage();
      mi[i] = (int) FastMath.ceil(resultMI / (i + 1));
      if(i != n) {
        mp[n - 1 - i] = (int) FastMath.ceil(resultMP / (i + 1));
      }
    }

    return new int[][] { mi, mp };
  }

  /**
   * The specified sorted list of dense subspaces is divided into the selected
   * set I and the pruned set P. For each set the difference from the specified
   * mean values is computed.
   * 
   * @param denseSubspaces denseSubspaces the dense subspaces in reverse order
   *        by their coverage
   * @param mi the mean of the selected sets I
   * @param mp the mean of the pruned sets P
   * @return the difference from the specified mean values, the first value is
   *         the difference from the mean of the selected set I, the second
   *         value is the difference from the mean of the pruned set P.
   */
  private double[][] computeDiffs(List<CLIQUESubspace<V>> denseSubspaces, int[] mi, int[] mp) {
    int n = denseSubspaces.size() - 1;

    double[] diff_mi = new double[n + 1], diff_mp = new double[n + 1];
    double resultMI = 0, resultMP = 0;

    for(int i = 0; i < denseSubspaces.size(); i++) {
      double diffMI = Math.abs(denseSubspaces.get(i).getCoverage() - mi[i]);
      resultMI += log2OrZero(diffMI);
      double diffMP = (i != n) ? Math.abs(denseSubspaces.get(n - i).getCoverage() - mp[n - 1 - i]) : 0;
      resultMP += log2OrZero(diffMP);
      diff_mi[i] = resultMI;
      if(i != n) {
        diff_mp[n - 1 - i] = resultMP;
      }
    }
    return new double[][] { diff_mi, diff_mp };
  }

  /**
   * Robust log 2, that ignores zero values.
   * 
   * @param x Input value
   * @return Log2(x), or zero.
   */
  private static double log2OrZero(double x) {
    return x > 0 ? MathUtil.log2(x) : 0;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<V extends NumberVector> extends AbstractParameterizer {
    /**
     * Parameter to specify the number of intervals (units) in each dimension,
     * must be an integer greater than 0.
     * <p>
     * Key: {@code -clique.xsi}
     * </p>
     */
    public static final OptionID XSI_ID = new OptionID("clique.xsi", "The number of intervals (units) in each dimension.");

    /**
     * Parameter to specify the density threshold for the selectivity of a unit,
     * where the selectivity is the fraction of total feature vectors contained
     * in
     * this unit, must be a double greater than 0 and less than 1.
     * <p>
     * Key: {@code -clique.tau}
     * </p>
     */
    public static final OptionID TAU_ID = new OptionID("clique.tau", "The density threshold for the selectivity of a unit, where the selectivity is" + "the fraction of total feature vectors contained in this unit.");

    /**
     * Flag to indicate that only subspaces with large coverage (i.e. the
     * fraction
     * of the database that is covered by the dense units) are selected, the
     * rest
     * will be pruned.
     * <p>
     * Key: {@code -clique.prune}
     * </p>
     */
    public static final OptionID PRUNE_ID = new OptionID("clique.prune", "Flag to indicate that only subspaces with large coverage " + "(i.e. the fraction of the database that is covered by the dense units) " + "are selected, the rest will be pruned.");

    protected int xsi;

    protected double tau;

    protected boolean prune;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      IntParameter xsiP = new IntParameter(XSI_ID) //
          .addConstraint(CommonConstraints.GREATER_EQUAL_ONE_INT);
      if(config.grab(xsiP)) {
        xsi = xsiP.intValue();
      }

      DoubleParameter tauP = new DoubleParameter(TAU_ID) //
          .addConstraint(CommonConstraints.GREATER_THAN_ZERO_DOUBLE) //
          .addConstraint(CommonConstraints.LESS_THAN_ONE_DOUBLE);
      if(config.grab(tauP)) {
        tau = tauP.doubleValue();
      }

      Flag pruneF = new Flag(PRUNE_ID);
      if(config.grab(pruneF)) {
        prune = pruneF.isTrue();
      }
    }

    @Override
    protected CLIQUE<V> makeInstance() {
      return new CLIQUE<>(xsi, tau, prune);
    }
  }
}
