/* ===========================================================
 * GTNA : Graph-Theoretic Network Analyzer
 * ===========================================================
 *
 * (C) Copyright 2009-2011, by Benjamin Schiller (P2P, TU Darmstadt)
 * and Contributors
 *
 * Project Info:  http://www.p2p.tu-darmstadt.de/research/gtna/
 *
 * GTNA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GTNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * ---------------------------------------
 * RepresentativeIdentifierTargetSelection.java
 * ---------------------------------------
 * (C) Copyright 2009-2011, by Benjamin Schiller (P2P, TU Darmstadt)
 * and Contributors
 *
 * Original Author: benni;
 * Contributors:    -;
 *
 * Changes since 2011-05-17
 * ---------------------------------------
 *
 */
package gtna.routing.selection.target;

import gtna.graph.Graph;
import gtna.id.Identifier;
import gtna.id.IdentifierSpace;

/**
 * @author benni
 */
public class RepresentativeIdTargetSelection extends TargetSelection {

  protected IdentifierSpace ids;

  public RepresentativeIdTargetSelection() {
    super("TARGET_SELECTION_REPRESENTATIVE_ID");
  }

  public void init(Graph graph) {
    super.init(graph);
    this.ids = (IdentifierSpace) this.graph.getProperty("ID_SPACE_0");
  }

  @Override
  public Identifier getNextTarget() {
    if (this.ids.getPartitions() == null) throw new IllegalArgumentException();
    return this.ids.getPartitions()[this.rand.nextInt(this.ids
            .getPartitions().length)].getRepresentativeIdentifier();
  }

  @Override
  public boolean applicable(Graph graph) {
    return graph.hasProperty("ID_SPACE_0", IdentifierSpace.class);
  }
}