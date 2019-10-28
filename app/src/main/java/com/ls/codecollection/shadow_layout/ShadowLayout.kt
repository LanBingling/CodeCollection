package com.ls.codecollection.shadow_layout

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import com.ls.codecollection.R
import kotlin.math.absoluteValue
import kotlin.properties.Delegates

/**
 * 创建画笔
 */
internal fun <T: View> T.createPaint(colorString: String? = null, @ColorInt color: Int? = null): Paint {
    return Paint().apply {
        this.utilReset(colorString, color)
    }
}

/**
 * 自定义画笔重置方法
 */
internal fun Paint.utilReset(colorString: String? = null, @ColorInt color: Int? = null) {
    this.reset()
    this.color = color ?: Color.parseColor(colorString ?: "#FFFFFF")
    this.isAntiAlias = true
    this.style = Paint.Style.FILL
    this.strokeWidth = 0F
}

/**
 * dp转px
 */
internal fun Context.dp2px(dp: Float): Float {
    return if (dp == 0F) 0F else dp * resources.displayMetrics.density + 0.5F
}

/**
 * Flags基本操作 FlagSet是否包含Flag
 */
internal fun Int.containsFlag(flag: Int): Boolean {
    return this or flag == this
}

class ShadowLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * 阴影颜色
     */
    @ColorInt
    private var mShadowColor: Int = 0
    /**
     * 阴影发散距离
     */
    private var mShadowWidth: Float = 0F
    /**
     * x轴偏移距离
     */
    private var mDx: Float = 0F
    /**
     * y轴偏移距离
     */
    private var mDy: Float = 0F
    /**
     * 布局圆角半径
     */
    private var mCornerRadius: Float = 0F
    /**
     * 边框颜色
     */
    @ColorInt
    private var mBorderColor: Int = 0
    /**
     * 边框宽度
     */
    private var mBorderWidth: Float = 0F
    /**
     * 控制四边是否显示阴影
     */
    private var mShadowSides: Int = DEF_SHADOW_SIDES

    /**
     * 全局画笔
     */
    private var mPaint: Paint = createPaint(color = Color.WHITE)

    /**
     * 全局Path
     */
    private var mPath = Path()
    /**
     * 合成模式
     */
    private var mXFerMode: PorterDuffXfermode by Delegates.notNull()
    /**
     * 视图内容区域的RectF实例
     */
    private var mContentRF: RectF by Delegates.notNull()
    /**
     * 视图边框的RectF实例
     */
    private var mBorderRF: RectF? = null

    init {
        initAttributes(context, attrs)
        initDrawAttributes()
        processPadding()
        //设置软件渲染类型
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * 初始化自定义属性
     */
    private fun initAttributes(context: Context, attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ShadowLayout)
        try {
            a.run {
                mShadowColor = getColor(R.styleable.ShadowLayout_shadowColor, DEF_SHADOW_COLOR)
                mShadowWidth = getDimension(R.styleable.ShadowLayout_shadowWidth, context.dp2px(DEF_SHADOW_RADIUS))
                mDx = getDimension(R.styleable.ShadowLayout_dx, DEF_DX)
                mDy = getDimension(R.styleable.ShadowLayout_dy, DEF_DY)
                mCornerRadius = getDimension(R.styleable.ShadowLayout_cornerRadius, context.dp2px(DEF_CORNER_RADIUS))
                mBorderColor = getColor(R.styleable.ShadowLayout_borderColor, DEF_BORDER_COLOR)
                mBorderWidth = getDimension(R.styleable.ShadowLayout_borderWidth, context.dp2px(DEF_BORDER_WIDTH))
                mShadowSides = getInt(R.styleable.ShadowLayout_shadowSides, DEF_SHADOW_SIDES)
            }
        } finally {
            a.recycle()
        }
    }

    /**
     * 初始化绘制相关的属性
     */
    private fun initDrawAttributes() {
        //使用xFerMode在图层上进行合成，处理圆角
        mXFerMode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    /**
     * 处理View的Padding为阴影留出空间
     */
    private fun processPadding() {
        val xPadding = (mShadowWidth + mDx.absoluteValue).toInt()
        val yPadding = (mShadowWidth + mDy.absoluteValue).toInt()

        setPadding(
                if (mShadowSides.containsFlag(FLAG_SIDES_LEFT)) xPadding else 0,
                if (mShadowSides.containsFlag(FLAG_SIDES_TOP)) yPadding else 0,
                if (mShadowSides.containsFlag(FLAG_SIDES_RIGHT)) xPadding else 0,
                if (mShadowSides.containsFlag(FLAG_SIDES_BOTTOM)) yPadding else 0
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mContentRF = RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                (w - paddingRight).toFloat(),
                (h - paddingBottom).toFloat()
        )

        //以边框宽度的三分之一，微调边框绘制位置，以在边框较宽时得到更好的视觉效果
        val bw = mBorderWidth / 3
        if (bw > 0) {
            mBorderRF = RectF(
                    mContentRF.left + bw,
                    mContentRF.top + bw,
                    mContentRF.right - bw,
                    mContentRF.bottom - bw
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas?) {
        if (canvas == null) return

        //绘制阴影
        drawShadow(canvas)

        //绘制子View
        drawChild(canvas) {
            super.dispatchDraw(it)
        }

        //绘制边框
        drawBorder(canvas)
    }

    /**
     * 绘制阴影
     */
    private fun drawShadow(canvas: Canvas) {
        canvas.save()

        mPaint.setShadowLayer(mShadowWidth, mDx, mDy, mShadowColor)
        canvas.drawRoundRect(mContentRF, mCornerRadius, mCornerRadius, mPaint)
        mPaint.utilReset()

        canvas.restore()
    }

    /**
     * 绘制子View
     */
    private fun drawChild(canvas: Canvas, block: (Canvas) -> Unit) {
        canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), mPaint, Canvas.ALL_SAVE_FLAG)

        //先绘制子控件
        block.invoke(canvas)

        //使用path构建四个圆角
        mPath = mPath.apply {
            addRect(
                    mContentRF,
                    Path.Direction.CW
            )
            addRoundRect(
                    mContentRF,
                    mCornerRadius,
                    mCornerRadius,
                    Path.Direction.CW
            )
            fillType = Path.FillType.EVEN_ODD
        }

        //使用xFerMode在图层上进行合成，处理圆角
        mPaint.xfermode = mXFerMode
        canvas.drawPath(mPath, mPaint)
        mPaint.utilReset()
        mPath.reset()

        canvas.restore()
    }

    /**
     * 绘制边框
     */
    private fun drawBorder(canvas: Canvas) {
        mBorderRF?.let {
            canvas.save()

            mPaint.strokeWidth = mBorderWidth
            mPaint.style = Paint.Style.STROKE
            mPaint.color = mBorderColor
            canvas.drawRoundRect(it, mCornerRadius, mCornerRadius, mPaint)
            mPaint.utilReset()

            canvas.restore()
        }
    }

    companion object {

        private const val FLAG_SIDES_TOP = 1
        private const val FLAG_SIDES_RIGHT = 2
        private const val FLAG_SIDES_BOTTOM = 4
        private const val FLAG_SIDES_LEFT = 8
        private const val FLAG_SIDES_ALL = 15

        const val DEF_SHADOW_SIDES = FLAG_SIDES_ALL
        const val DEF_SHADOW_COLOR = Color.BLACK
        const val DEF_BORDER_COLOR = Color.WHITE
        const val DEF_BORDER_WIDTH = 0F
        const val DEF_SHADOW_RADIUS = 0F
        const val DEF_CORNER_RADIUS = 0F
        const val DEF_DX = 0F
        const val DEF_DY = 0F

    }

}