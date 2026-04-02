package it.unibo.collektive.models

import kotlin.math.hypot

/**
 * A simple data class representing a point in 2D space.
 *
 * @property x The x-coordinate of the point.
 * @property y The y-coordinate of the point.
 */
data class Point(val x: Double, val y: Double)

/**
 * A data class representing a particle in a Particle Filter.
 *
 * @property x The x-coordinate of the particle's position.
 * @property y The y-coordinate of the particle's position.
 * @property vx The velocity of the particle along the x-axis.
 * @property vy The velocity of the particle along the y-axis.
 * @property weight The weight of the particle, representing its importance.
 */
data class Particle(var x: Double, var y: Double, var vx: Double, var vy: Double, var weight: Double = 1.0)

/**
 * Operator overloads for Point class to facilitate vector arithmetic.
 * @receiver Point the first point
 * @param other Point the second point
 * @return Point the resulting point after addition
 */
operator fun Point.plus(other: Point): Point = Point(this.x + other.x, this.y + other.y)

/**
 * Operator overloads for Point class to facilitate vector arithmetic.
 * @receiver Point the first point
 * @param other Point the second point
 * @return Point the resulting point after subtraction
 */
operator fun Point.div(scalar: Double): Point = Point(this.x / scalar, this.y / scalar)

fun Point.distanceTo(other: Point): Double = hypot(this.x - other.x, this.y - other.y)
