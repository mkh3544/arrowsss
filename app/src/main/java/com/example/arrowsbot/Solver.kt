package com.example.arrowsbot

import android.graphics.Bitmap
import android.graphics.Point
import kotlin.math.abs
import kotlin.math.max

object Config {
    const val SCALE = 2
    const val TOP = 0.13f
    const val BOTTOM = 0.87f
    const val DIFF = 85
    const val MIN_AREA = 35
    const val CONT_X = 0.5f
    const val CONT_Y = 0.82f
    const val TAP_DELAY = 280L
    const val CYCLE_DELAY = 1100L
}

object Solver {
    fun findTaps(src: Bitmap): List<Point> {
        val s = Config.SCALE
        val w = src.width / s
        val h = src.height / s
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== src) scaled.recycle()
        val y0 = (h * Config.TOP).toInt()
        val y1 = (h * Config.BOTTOM).toInt()
        val hist = HashMap<Int, Int>()
        for (y in y0 until y1 step 3)
            for (x in 0 until w step 3) {
                val k = px[y * w + x] and 0xF0F0F0
                hist[k] = (hist[k] ?: 0) + 1
            }
        if (hist.isEmpty()) return emptyList()
        val bg = hist.maxByOrNull { it.value }!!.key
        val br = ((bg shr 16) and 255) + 8
        val bgc = ((bg shr 8) and 255) + 8
        val bb = (bg and 255) + 8
        val ink = BooleanArray(w * h)
        for (y in y0 until y1)
            for (x in 0 until w) {
                val c = px[y * w + x]
                val d = abs(((c shr 16) and 255) - br) +
                    abs(((c shr 8) and 255) - bgc) + abs((c and 255) - bb)
                ink[y * w + x] = d > Config.DIFF
            }

        val label = IntArray(w * h)
        val order = IntArray(w * h)
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        var qEnd = 0
        for (i in 0 until w * h) {
            if (!ink[i] || label[i] != 0) continue
            val id = starts.size + 1
            val st = qEnd
            var qh = qEnd
            order[qEnd++] = i
            label[i] = id
            while (qh < qEnd) {
                val p = order[qh++]
                val px0 = p % w
                val py0 = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px0 + dx
                    val ny = py0 + dy
                    if (nx < 0 || ny < y0 || nx >= w || ny >= y1) continue
                    val n = ny * w + nx
                    if (ink[n] && label[n] == 0) {
                        label[n] = id
                        order[qEnd++] = n
                    }
                }
            }
            starts.add(st)
            ends.add(qEnd)
        }

        val maxArea = (y1 - y0) * w / 3
        val valid = BooleanArray(starts.size + 1) {
            it > 0 && (ends[it - 1] - starts[it - 1]) in Config.MIN_AREA..maxArea
        }
        val taps = ArrayList<Point>()
        for (id in 1..starts.size) {
            if (!valid[id]) continue
            val a = starts[id - 1]
            val b = ends[id - 1]
            val area = b - a
            var sx = 0L
            var sy = 0L
            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0
            for (k in a until b) {
                val x = order[k] % w
                val y = order[k] / w
                sx += x
                sy += y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            val cx = sx.toFloat() / area
            val cy = sy.toFloat() / area
            fun far(fx: Float, fy: Float): Point {
                var best = Point(0, 0)
                var bd = -1f
                for (k in a until b) {
                    val x = order[k] % w
                    val y = order[k] / w
                    val d = (x - fx) * (x - fx) + (y - fy) * (y - fy)
                    if (d > bd) { bd = d; best = Point(x, y) }
                }
                return best
            }
            val e1 = far(cx, cy)
            val e2 = far(e1.x.toFloat(), e1.y.toFloat())
            val stroke = max(2f, area.toFloat() / ((maxX - minX) + (maxY - minY) + 1))
            val r = max(6f, 3 * stroke)
            fun near(p: Point, rad: Float): Int {
                var n = 0
                for (k in a until b) {
                    val x = order[k] % w - p.x
                    val y = order[k] / w - p.y
                    if (x * x + y * y <= rad * rad) n++
                }
                return n
            }
            val head = if (near(e1, r) >= near(e2, r)) e1 else e2
            var hx = 0f
            var hy = 0f
            var hn = 0
            for (k in a until b) {
                val x = order[k] % w
                val y = order[k] / w
                val dx = x - head.x
                val dy = y - head.y
                if (dx * dx + dy * dy <= 9 * r * r) {
                    hx += x
                    hy += y
                    hn++
                }
            }
            if (hn == 0) continue
            hx /= hn
            hy /= hn
            val vx = head.x - hx
            val vy = head.y - hy
            val dx = if (abs(vx) >= abs(vy)) if (vx >= 0) 1 else -1 else 0
            val dy = if (abs(vx) >= abs(vy)) 0 else if (vy >= 0) 1 else -1
            val hw = max(1, (stroke * 0.5f).toInt())
            var x = head.x
            var y = head.y
            var blocked = false
            while (true) {
                x += dx
                y += dy
                if (x < 0 || y < 0 || x >= w || y >= h) break
                for (o in -hw..hw) {
                    val qx = x + if (dy != 0) o else 0
                    val qy = y + if (dx != 0) o else 0
                    if (qx < 0 || qy < 0 || qx >= w || qy >= h) continue
                    val l = label[qy * w + qx]
                    if (l != 0 && l != id && valid[l]) {
                        blocked = true
                        break
                    }
                }
                if (blocked) break
            }
            if (!blocked) {
                taps.add(Point(((head.x + hx) / 2 * s).toInt(),
                    ((head.y + hy) / 2 * s).toInt()))
            }
        }
        return taps
    }
}
