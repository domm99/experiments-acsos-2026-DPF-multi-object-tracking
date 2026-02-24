package it.unibo.alchemist

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.actions.AbstractMoveNode
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import java.util.Random
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Represents a movement action that updates the position of a node within a Euclidean 2D environment.
 * This class defines the motion dynamics, including velocity, boundaries, and trajectory behavior.
 *
 * @param T the concentration type managed by the node
 * @param environment the simulation environment in which the node resides
 * @param node the node under movement
 * @param xVel the initial velocity of the node along the x-axis
 * @param yVel the initial velocity of the node along the y-axis
 * @param sideLength the boundary limits of the environment (assumes a square-shaped area)
 * @param accelerationFactor the acceleration factor applied to the node's movement
 */
class MoveNode<T>(
    environment: Environment<T, Euclidean2DPosition>,
    node: Node<T>,
    val xVel: Double,
    val yVel: Double,
    val sideLength: Double,
    val accelerationFactor: Double,
) : AbstractMoveNode<T, Euclidean2DPosition>(environment, node, true) {

    private var currentVx = xVel
    private var currentVy = yVel
    private var currentScalarVelocity: Double = 0.0
    private var friction: Double = 0.97

    override fun getNextPosition(): Euclidean2DPosition? {
        val currentPosition = environment.getPosition(node)
        storePosition(currentPosition)
        var newPosition = computeNextPosition(currentPosition)
        newPosition = checkBoundaries(newPosition)
        return newPosition
    }

    private fun storePosition(currentPosition: Euclidean2DPosition) {
        val node =
            environment.nodes.first { it.contains(SimpleMolecule("Movable")) }
        node
            .setConcentration(SimpleMolecule("PositionX"), currentPosition.x as T?)

        node
            .setConcentration(SimpleMolecule("PositionY"), currentPosition.y as T?)
    }

    override fun cloneAction(p0: Node<T?>?, p1: Reaction<T?>?): Action<T?>? =
        MoveNode(environment, node, xVel, yVel, sideLength, accelerationFactor)

    private fun trajectoryFunction(x: Double): Double {
        return 0.5 * x + 5 * kotlin.math.sin(0.3 * x)
        // x + kotlin.math.sin(x)
    }

    private fun computeNextPosition(currentPosition: Euclidean2DPosition): Euclidean2DPosition {
        currentScalarVelocity = (currentScalarVelocity * friction) + accelerationFactor
        val newX = currentPosition.x + currentScalarVelocity
        val newY = trajectoryFunction(newX)
        return Euclidean2DPosition(newX, newY)
    }

    private fun checkBoundaries(position: Euclidean2DPosition): Euclidean2DPosition {
        var newX = position.x
        var newY = position.y

        if (newX < 0) {
            newX = 0.0
            currentVx = -currentVx
        } else if (newX > sideLength) {
            newX = sideLength
            currentVx = -currentVx
        }

        if (newY < 0) {
            newY = 0.0
            currentVy = -currentVy
        } else if (newY > sideLength) {
            newY = sideLength
            currentVy = -currentVy
        }
        return Euclidean2DPosition(newX, newY)
    }
}
