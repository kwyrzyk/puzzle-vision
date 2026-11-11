package io.github.kwyrzyk.puzzlevision.cube

import io.github.kwyrzyk.puzzlevision.vision.Lab
import io.github.kwyrzyk.puzzlevision.vision.Params
import java.util.PriorityQueue

/**
 * Colors of the cube from the 6 captures, method C++ of notebook 3.1.
 *
 * [faces] are in [PHOTO_ORDER], each 9 colors in reading order (Lab, L weighted). Pieces go
 * to the legal state closest to the center colors; the colors are then named by the rotation
 * of the cube closest to the palette, so the first face may be any.
 */
fun classify(faces: List<List<Lab>>, p: Params): CubeState {
    val features = featuresOf(faces)
    val state = pieceAssignment(features)
    return lowestCostColorsFitted(state, features, paletteOf(p))
}

/** Color of each facelet: face k of [faces] is [PHOTO_ORDER] k, turned as [PHOTO_TOP] */
private fun featuresOf(faces: List<List<Lab>>): List<Lab> {
    val features = arrayOfNulls<Lab>(FACELETS.size)
    for ((k, colors) in faces.withIndex()) {
        val face = PHOTO_ORDER[k]
        faceView(face, PHOTO_TOP.getValue(face)).forEachIndexed { cell, index ->
            features[idOf(face, index)] = colors[cell]
        }
    }
    return features.requireNoNulls().toList()
}

// Piece assignment (B++ in notebook 3.1)

/** Pieces of the places, their orientations and the cost */
private class Oriented(val cost: Double, val pieces: List<Int>, val orientations: List<Int>)

/**
 * Legal state closest to the colors, colors named by the geometric faces.
 *
 * Each corner and edge goes to one place in one orientation; cost = distances of its facelets
 * to the center colors. Twist and flip sums by dynamic programming, parity by the next best
 * assignments, until both parities are found.
 */
private fun pieceAssignment(features: List<Lab>): List<Face> {
    val refs = Face.entries.map { features[idOf(it, CENTER)] }
    val corners = bestByParity(costs(features, refs, CORNERS))
    val edges = bestByParity(costs(features, refs, EDGES))
    val parity = (0..1).minBy { corners.getValue(it).cost + edges.getValue(it).cost }

    val colors = Array(FACELETS.size) { FACELETS[it].face } // centers stay, the rest is set below
    for ((names, best) in listOf(CORNERS to corners, EDGES to edges)) {
        val chosen = best.getValue(parity)
        for ((place, name) in names.withIndex()) {
            val piece = names[chosen.pieces[place]]
            val o = chosen.orientations[place]
            placeOf(name).forEachIndexed { j, id ->
                colors[id] = faceOf(piece[(j - o).mod(piece.length)])
            }
        }
    }
    return colors.toList()
}

/** cost[place][piece][orientation]; orientation o puts color k of the piece on facelet k + o */
private fun costs(features: List<Lab>, refs: List<Lab>, names: List<String>) =
    names.map { place ->
        val points = placeOf(place).map { features[it] }
        names.map { piece ->
            val n = piece.length
            DoubleArray(n) { o ->
                (0 until n).sumOf { j ->
                    val color = faceOf(piece[(j - o).mod(n)])
                    points[j].distanceTo(refs[color.ordinal])
                }
            }
        }
    }

/** Cheapest oriented assignment of each parity; the cost without orientation bounds the search */
private fun bestByParity(cost: List<List<DoubleArray>>): Map<Int, Oriented> {
    val bound = cost.map { row -> DoubleArray(row.size) { q -> row[q].min() } }
    val best = mutableMapOf<Int, Oriented>()
    for ((lower, pieces) in kBest(bound)) {
        if (best.size == 2 && lower >= best.values.maxOf { it.cost }) break
        val candidate = oriented(cost, pieces)
        val parity = inversions(pieces) % 2
        val current = best[parity]
        if (current == null || candidate.cost < current.cost) best[parity] = candidate
    }
    return best
}

/** Cheapest orientations of the pieces with the total twist (or flip) 0 */
private fun oriented(cost: List<List<DoubleArray>>, pieces: List<Int>): Oriented {
    val n = cost[0][0].size
    var best = mapOf(0 to Oriented(0.0, pieces, emptyList())) // by twist so far
    for ((place, piece) in pieces.withIndex()) {
        val step = mutableMapOf<Int, Oriented>()
        for ((twist, sofar) in best) {
            for (o in 0 until n) {
                val key = (twist + o) % n
                val value = sofar.cost + cost[place][piece][o]
                val current = step[key]
                if (current == null || value < current.cost) {
                    step[key] = Oriented(value, pieces, sofar.orientations + o)
                }
            }
        }
        best = step
    }
    return best.getValue(0)
}

private fun inversions(pieces: List<Int>): Int =
    pieces.indices.sumOf { i -> (i + 1 until pieces.size).count { pieces[i] > pieces[it] } }

private const val BIG = 1e9 // cost of forbidden pair; above any total of real costs

private typealias Cells = List<Pair<Int, Int>>  // (row, columns) pairs

private class Node (
    val cost: Double,
    val order: Int, // ties: the first found first
    val columns: List<Int>,
    val forced: Cells,
    val banned: Cells,
)

/** Assignment of rows to columns in increasing cost (Murty) */
private fun kBest(w: List<DoubleArray>): Sequence<Pair<Double, List<Int>>> = sequence {
    // Cheapest assignment with the forced pairs and without the banned ones, null if none
    fun solve(forced: Cells, banned: Cells): Pair<Double, List<Int>>? {
        val m = w.map { it.copyOf() }
        for ((p, q) in banned) m[p][q] = BIG
        for ((p, q) in forced) {
            for (j in m.indices) m[p][j] = BIG
            for (i in m.indices) m[i][q] = BIG
            m[p][q] = w[p][q]
        }
        val columns = hungarian(m)
        val cost = columns.indices.sumOf { m[it][columns[it]] }
        return if (cost >= BIG) null else cost to columns
    }
    var count = 0
    val heap = PriorityQueue<Node>(compareBy<Node>({ it.cost }, { it.order }))
    val (cost, columns)  = solve(emptyList(), emptyList())!!
    heap.add(Node(cost, count++, columns, emptyList(), emptyList()))
    while (heap.isNotEmpty()) {
        val node = heap.poll()!!
        yield(node.cost to node.columns)
        val forcedRows = node.forced.map { it.first }.toSet()
        val free = node.columns.indices.filter { it !in forcedRows}
        for ((k, p) in free.withIndex()) {
            val forced = node.forced + free.take(k).map { it to node.columns[it] }
            val banned = node.banned + (p to node.columns[p])
            val found = solve(forced, banned) ?: continue
            heap.add(Node(found.first, count++, found.second, forced, banned))
        }
    }
}

/** Cheapest assignment of a square cost matrix (Hungarian algorithm): the column of each row */
private fun hungarian(a: List<DoubleArray>): List<Int> {
    val n = a.size
    // 1-based as in the classic formulation; column 0 is a virtual start
    val u = DoubleArray(n +1)
    val v = DoubleArray(n +1)
    val rowOf = IntArray(n + 1) // row assigned to each column
    val way = IntArray(n + 1)
    for (i in 1..n) {
        rowOf[0] = i
        var j0 = 0
        val minv = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
        val used = BooleanArray(n + 1)
        do {
            used[j0] = true
            val i0 = rowOf[j0]
            var delta = Double.POSITIVE_INFINITY
            var j1 = 0
            for (j in 1..n) {
                if (used[j]) continue
                val reduced = a[i0 - 1][j - 1] - u[i0] - v[j]
                if (reduced < minv[j]) {
                    minv[j] = reduced
                    way[j] = j0
                }
                if (minv[j] < delta) {
                    delta = minv[j]
                    j1 = j
                }
            }
            for (j in 0..n) {
                if (used[j]) {
                    u[rowOf[j]] += delta
                    v[j] -= delta
                } else {
                    minv[j] -= delta
                }
            }
            j0 = j1
        } while (rowOf[j0] != 0)
        do {
            val j1 = way[j0]
            rowOf[j0] = rowOf[j1]
            j0 = j1
        } while (j0 != 0)
    }
    val columns = IntArray(n)
    for (j in 1..n) columns[rowOf[j] - 1] = j - 1
    return columns.toList()
}

// Naming (C++)

/** Palette of notebook 3.1 in Lab; L is weighted by `l_weight` as the sticker colors */
private fun paletteOf(p: Params): Map<Face, Lab> = mapOf(
    Face.U to Lab(100.0, 0.0, 0.0),
    Face.R to Lab(47.296142578125, 68.5625, 45.328125),
    Face.F to Lab(57.16552734375, -60.78125, 58.671875),
    Face.D to Lab(83.282470703125, -10.078125, 83.484375),
    Face.L to Lab(67.4560546875, 41.84375, 74.25),
    Face.B to Lab(32.635498046875, 41.1875, -75.671875),
).mapValues { (_, lab) -> lab.copy(l = lab.l * p.lWeight) }

/**
 * Cube can be held in 24 different orientations - 6 faces x 4 rotations around face normal.
 * Each maps the name of a color group to its new name, e.g. R -> B, F -> R, L -> F, B -> L.
 */
private val ROTATIONS: List<Map<Face, Face>> = permutations(Face.entries)
    .map { image -> Face.entries.zip(image).toMap() } // U to image[0], R to image [1], ...
    .filter { image ->
        val x = image.getValue(Face.R).normal
        val y = image.getValue(Face.U).normal
        val z = image.getValue(Face.F).normal
        (x dot (y cross z)) == 1 && Face.entries.all { face ->
            val n = face.normal
            image.getValue(face).normal == x * n.x + y * n.y + z * n.z
        }
    }

/** All orders of items, in lexicographic order of their positions */
private fun <T> permutations(items: List<T>): List<List<T>> =
    if (items.size <= 1) listOf(items)
    else items.indices.flatMap { i ->
        permutations(items.filterIndexed { j, _ -> j != i  }).map { listOf(items[i]) + it}
    }

/** Each color group of 9 facelets named by the rotation closest to the palette */
private fun lowestCostColorsFitted(
    state: List<Face>,
    features: List<Lab>,
    palette: Map<Face, Lab>,
): CubeState {
    val groups = Face.entries.map { color ->
        meanOf(features.filterIndexed { id, _ -> state[id] == color })
    }
    val rotation = ROTATIONS.minBy { image ->
        Face.entries.sumOf { groups[it.ordinal].distanceTo(palette.getValue(image.getValue(it))) }
    }
    return CubeState(state.map { rotation.getValue(it) })
}

private fun meanOf(colors: List<Lab>) = Lab(
    colors.map { it.l }.average(),
    colors.map { it.a }.average(),
    colors.map { it.b }.average(),
)