package com.hufs.arnavigation_com

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var angleDiff: Float = 0f
        set(value) { field = value; invalidate() }

    private val density = context.resources.displayMetrics.density

    // navCard 아래 시작점 (대략 160dp 아래부터 점 시작)
    private val navCardBottomDp = 160f

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
    }
    private val arrowDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A56C4")
        style = Paint.Style.FILL
    }
    private val arrowBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D3A8A")
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
    }
    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val camera3D = Camera()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f

        // 화살표 중심: 화면 하단에서 약 15% 위
        val arrowCy = height * 0.85f
        val arrowSize = height * 0.08f

        // navCard 아래 시작 Y
        val dotStartY = navCardBottomDp * density

        canvas.save()
        canvas.rotate(angleDiff, cx, arrowCy)

        // ── 점선: navCard 아래부터 화살표 끝까지 ──
        val arrowTipY = arrowCy - arrowSize  // 화살표 뾰족한 끝
        val dotRadius = 8f
        val dotSpacing = 60f
        var currentY = arrowTipY - dotSpacing

        while (currentY > dotStartY) {
            canvas.drawCircle(cx, currentY, dotRadius, dotPaint)
            canvas.drawCircle(cx, currentY, dotRadius, dotStrokePaint)
            currentY -= dotSpacing
        }

        // ── 화살표 3D 원근감 ──
        val matrix = Matrix()
        camera3D.save()
        camera3D.rotateX(40f)
        camera3D.getMatrix(matrix)
        camera3D.restore()
        matrix.preTranslate(-cx, -arrowCy)
        matrix.postTranslate(cx, arrowCy)
        canvas.concat(matrix)

        // 아랫면 (그림자)
        val offsetY = arrowSize * 0.12f
        val bottomPath = Path().apply {
            moveTo(cx, arrowCy - arrowSize + offsetY)
            lineTo(cx + arrowSize * 0.5f, arrowCy + arrowSize * 0.3f + offsetY)
            lineTo(cx, arrowCy + arrowSize * 0.1f + offsetY)
            lineTo(cx - arrowSize * 0.5f, arrowCy + arrowSize * 0.3f + offsetY)
            close()
        }
        canvas.drawPath(bottomPath, arrowBottomPaint)

        // 오른쪽 삼각형
        val rightPath = Path().apply {
            moveTo(cx, arrowCy - arrowSize)
            lineTo(cx + arrowSize * 0.5f, arrowCy + arrowSize * 0.3f)
            lineTo(cx, arrowCy + arrowSize * 0.1f)
            close()
        }
        canvas.drawPath(rightPath, arrowPaint)

        // 왼쪽 삼각형
        val leftPath = Path().apply {
            moveTo(cx, arrowCy - arrowSize)
            lineTo(cx - arrowSize * 0.5f, arrowCy + arrowSize * 0.3f)
            lineTo(cx, arrowCy + arrowSize * 0.1f)
            close()
        }
        canvas.drawPath(leftPath, arrowDarkPaint)

        canvas.restore()
    }
}