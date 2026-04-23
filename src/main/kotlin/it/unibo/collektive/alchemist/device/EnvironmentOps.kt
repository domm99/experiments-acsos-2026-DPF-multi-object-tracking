package it.unibo.collektive.alchemist.device

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.models.Point

/**
 * Computes the centroid of all nodes marked as filter nodes in the current environment.
 *
 * @return The arithmetic mean of filter node positions, or the origin when no filters exist.
 */
fun <T, P : Position<P>> Environment<T, P>.currentFiltersCentroid(): Point {
    val filters = nodes.filter { it.contains(SimpleMolecule("Filter")) }
    if (filters.isEmpty()) return Point(0.0, 0.0)
    val sumX = filters.sumOf { getPosition(it).coordinates[0] }
    val sumY = filters.sumOf { getPosition(it).coordinates[1] }
    return Point(sumX / filters.size, sumY / filters.size)
}

/**
 * Resolves the zero-based index of a filter node within the sorted filter-node id list.
 *
 * @param nodeId Identifier of the node whose index is required.
 * @return The filter index, or `0` when the node is not part of the filter set.
 */
fun <T, P : Position<P>> Environment<T, P>.filterIndexOf(nodeId: Int): Int = nodes
    .filter { it.contains(SimpleMolecule("Filter")) }
    .map { it.id }.sorted().indexOf(nodeId).takeIf { it >= 0 } ?: 0
