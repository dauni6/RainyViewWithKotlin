package com.example.rainyviewwithkotlin

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

/**
 * 호출 순서
 * 1) init() - 1번
 * 2) onMeasure() - 2번
 * 3) onSizeChanged() - 1번
 * 4) onDraw() - 여러번 계속
 * */
class RainyView : View {

    companion object {
        private const val DEFAULT_SIZE = 300 //the default size if set "wrap_content"
        private const val DEFAULT_DROP_MAX_NUMBER = 30 //Number of raindrops that can coexist at the same time
        private const val DEFAULT_DROP_CREATION_INTERVAL = 50 //the default drop creation interval in millis
        private const val DEFAULT_DROP_MAX_LENGTH = 50 //the default max length of drop
        private const val DEFAULT_DROP_MIN_LENGTH = 10 //the default max length of drop
        private const val DEFAULT_DROP_SIZE = 15 //the default drop size of drop
        private const val DEFAULT_DROP_MAX_SPEECH = 5f //the default max speech value
        private const val DEFAULT_DROP_MIN_SPEECH = 1f //the default max speech value
        private const val DEFAULT_DROP_SLOPE = -3f // the default drop slope 빗방울 경사 방항(양수 : 오른쪽 방향으로 내림, 음수 : 왼쪽방향으로 내림)

        private val DEFAULT_LEFT_CLOUD_COLOR = Color.parseColor("#B0B0B0")
        private val DEFAULT_RIGHT_CLOUD_COLOR = Color.parseColor("#DFDFDF")
        private val DEFAULT_RAIN_COLOR = Color.parseColor("#80B9C5")
        private const val CLOUD_SCALE_RATIO = 0.85f
    }

    private var mLeftCloudPaint: Paint? = null
    private var mRightCloudPaint: Paint? = null
    private var mRainPaint: Paint? = null

    private var mLeftCloudColor: Int = DEFAULT_LEFT_CLOUD_COLOR
    private var mRightCloudColor: Int = DEFAULT_RIGHT_CLOUD_COLOR
    private var mRainColor: Int = DEFAULT_RAIN_COLOR

    //There are two clouds in this view, includes the left cloud & right cloud
    private var mLeftCloudPath: Path? = null //the left cloud's path
    private var mRightCloudPath: Path? = null //the right cloud's path

    private var mRainRect: RectF? = null //the rain rect
    private var mRainClipRect: RectF? = null //the rain clip rect

    private var mLeftCloudAnimator: ValueAnimator? = null
    private var mRightCloudAnimator: ValueAnimator? = null

    private var mLeftCloudAnimatorPlayTime: Long = 0
    private var mRightCloudAnimatorPlayTime: Long = 0

    private var mMaxTranslationX = 0f //The max translation x when do animation.

    private var mLeftCloudAnimatorValue = 0f //The left cloud animator value

    private var mRightCloudAnimatorValue = 0f //The right cloud animator value

    private val mComputePath = Path() //The path for computing

    private val mComputeMatrix = Matrix() //The matrix for computing

    private var mRainDrops: ArrayList<RainDrop>? = null //all the rain drops
    private var mRemovedRainDrops: ArrayList<RainDrop>? = null //help to record the removed drops, avoid "java.util.ConcurrentModificationException"
    private var mRecycler: Stack<RainDrop>? = null

    private val mOnlyRandom = Random() //the only random object

    private var mHandler: Handler? = Handler(Looper.getMainLooper()) //help to update the raindrops state task

    private var mRainDropMaxNumber: Int = DEFAULT_DROP_MAX_NUMBER
    private var mRainDropCreationInterval: Int = DEFAULT_DROP_CREATION_INTERVAL
    private var mRainDropMinLength: Int = DEFAULT_DROP_MIN_LENGTH
    private var mRainDropMaxLength: Int = DEFAULT_DROP_MAX_LENGTH
    private var mRainDropSize: Int = DEFAULT_DROP_SIZE

    private var mRainDropMaxSpeed: Float = DEFAULT_DROP_MAX_SPEECH
    private var mRainDropMinSpeed: Float = DEFAULT_DROP_MIN_SPEECH
    private var mRainDropSlope: Float = DEFAULT_DROP_SLOPE

    private var mRainDropCreationTime: Long = 0L

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        parseAttrs(attrs)
        init()
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        parseAttrs(attrs)
        init()
    }

    private fun parseAttrs(attrs: AttributeSet?) {
        if (attrs == null) return

        // 지정한 <declare-styleable> attributes 로드하기
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RainyView)
        // 정의한 color값을 가져오거나 default값들을 사용하기
        mLeftCloudColor = typedArray.getColor(R.styleable.RainyView_left_cloud_color, DEFAULT_LEFT_CLOUD_COLOR)
        mRightCloudColor = typedArray.getColor(R.styleable.RainyView_right_cloud_color, DEFAULT_RIGHT_CLOUD_COLOR)
        mRainColor = typedArray.getColor(R.styleable.RainyView_raindrop_color, DEFAULT_RAIN_COLOR)
        mRainDropMaxNumber = typedArray.getInteger(R.styleable.RainyView_raindrop_max_number, DEFAULT_DROP_MAX_NUMBER)
        mRainDropMinLength = typedArray.getInteger(R.styleable.RainyView_raindrop_min_length, DEFAULT_DROP_MIN_LENGTH)
        mRainDropMaxLength = typedArray.getInteger(R.styleable.RainyView_raindrop_max_length, DEFAULT_DROP_MAX_LENGTH)
        mRainDropMinSpeed = typedArray.getFloat(R.styleable.RainyView_raindrop_min_speed, DEFAULT_DROP_MIN_SPEECH)
        mRainDropMaxSpeed = typedArray.getFloat(R.styleable.RainyView_raindrop_max_speed, DEFAULT_DROP_MAX_SPEECH)
        mRainDropCreationInterval = typedArray.getInteger(R.styleable.RainyView_raindrop_creation_interval, DEFAULT_DROP_CREATION_INTERVAL)
        mRainDropSize = typedArray.getInteger(R.styleable.RainyView_raindrop_size, DEFAULT_DROP_SIZE)
        mRainDropSlope = typedArray.getFloat(R.styleable.RainyView_raindrop_slope, DEFAULT_DROP_SLOPE)

        typedArray.recycle() // typedArray를 더 이상 사용하지 않으면 recycle()을 통해 참조를 메모리에서 제거해준다.
        // 굳이 하지 않아도 GC가 나중에 알아서 없애주기도 하지만 명시적으로 하라고 Lint를 띄워준다.
        // 그러나 나중에 재사용될 수 있으므로 recycle() 해주도록 하자.

        checkValue()
    }

    private fun checkValue() {
        checkRainDropCreationIntervalValue()
        checkRainDropLengthValue()
        checkRainDropManNumberValue()
        checkRainDropSizeValue()
        checkRainDropSpeedValue()
        checkRainDropSlopeValue()
    }

    // 필요한 Paint, Path, RectF 및 빗방울 갯수 등을 초기화해놓는 메서드
    private fun init() {
        // 아래의 코드는 View의 하드웨어 가속 옵션을 비활성화하는 코드.
        // 하드웨어 가속을했을 때 오히려 Bitmap이 이상하게 나오는 오류가 있다고 함. 그래서 비활성화한 듯 하다. 활성화하면 어떻게 나올까?
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // 하드웨어 가속화가 되어있는지 확인하기 위한 코드
        Log.d("TEST", "View is HardwareAccelerated() is $isHardwareAccelerated")
        mLeftCloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mLeftCloudColor
            style = Paint.Style.FILL
        }

        mRightCloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mRightCloudColor
            style = Paint.Style.FILL
        }

        mRainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            color = mRainColor
            style = Paint.Style.STROKE
            strokeWidth = mRainDropSize.toFloat()
        }

        mLeftCloudPath = Path()
        mRightCloudPath = Path()
        mRainRect = RectF()
        mRainClipRect = RectF()

        mRainDrops = ArrayList(mRainDropMaxNumber)
        mRemovedRainDrops = ArrayList(mRainDropMaxNumber)
        mRecycler = Stack()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        Log.d("TEST", "onMeasure() is called.")
        val widthSpecSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)

//        Log.d("TEST", "widthSpecSize = $widthSpecSize / widthSpecMode = $widthSpecMode")

        val heightSpecSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)

//        Log.d("TEST", "heightSpecSize = $heightSpecSize / heightSpecMode = $heightSpecMode")

        var w = widthSpecSize
        var h = heightSpecSize

        // MeasureSpec.AT_MOST : 자식뷰는 지정된 크기까지 원하는 만큼 커질 수 있다.
        // MeasureSpec.EXACTLY : 부모뷰가 자식뷰의 정확한 크기를 결정한다. 자식뷰의 사이즈와 관계없이 주어진 경계내에서 사이즈가 결정된다.
        // MeasureSpec.UNSPECIFIED : 부모뷰가 자식뷰에 제한을 두지 않기 때문에, 자식뷰는 원하는 크기가 될 수 있다.
        if (widthSpecMode == MeasureSpec.AT_MOST && heightSpecMode == MeasureSpec.AT_MOST) {
            w = DEFAULT_SIZE
            h = DEFAULT_SIZE
        } else if (widthSpecMode == MeasureSpec.AT_MOST) {
            w = DEFAULT_SIZE
            h = heightSpecSize
        } else if (heightSpecMode == MeasureSpec.AT_MOST) {
            w = widthSpecSize
            h = DEFAULT_SIZE
        }

        setMeasuredDimension(w, h) // onMeasure()는 값을 반환하지 않고, setMeasureDimension()을 호출하여 너비와 높이를 명시적으로 설정한다.
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        Log.d("TEST", "onSizeChanged() is called.")
        super.onSizeChanged(w, h, oldw, oldh)
        stop()

        mLeftCloudPath?.reset()
        mRightCloudPath?.reset()

        val centerX = (w / 2).toFloat() //view's center x coordinate

        val minSize = min(w, h).toFloat() //get the min size

        //************************compute left cloud**********************
        val leftCloudWidth = minSize / 2.5f //the width of cloud
        val leftCloudBottomHeight = leftCloudWidth / 3f //the bottom height of cloud
        val rightCloudTranslateX = leftCloudWidth * 2 / 3 //the distance of the cloud on the right
        val leftCloudEndX: Float = (w - leftCloudWidth - leftCloudWidth * CLOUD_SCALE_RATIO / 2) / 2 + leftCloudWidth //the left cloud end x coordinate
        val leftCloudEndY = (h / 3).toFloat() //clouds' end y coordinate

        //add the bottom round rect
        mLeftCloudPath?.addRoundRect(
            RectF(leftCloudEndX - leftCloudWidth, leftCloudEndY - leftCloudBottomHeight, leftCloudEndX, leftCloudEndY), leftCloudBottomHeight, leftCloudBottomHeight, Path.Direction.CW
        )

        val leftCloudTopCenterY = leftCloudEndY - leftCloudBottomHeight
        val leftCloudRightTopCenterX = leftCloudEndX - leftCloudBottomHeight
        val leftCloudLeftTopCenterX = leftCloudEndX - leftCloudWidth + leftCloudBottomHeight

        mLeftCloudPath?.addCircle(
            leftCloudRightTopCenterX,
            leftCloudTopCenterY,
            leftCloudBottomHeight * 3 / 4,
            Path.Direction.CW
        )
        mLeftCloudPath?.addCircle(
            leftCloudLeftTopCenterX,
            leftCloudTopCenterY,
            leftCloudBottomHeight / 2,
            Path.Direction.CW
        )
        //*******************************Done*****************************

        //************************compute right cloud**********************
        //The cloud on the right is CLOUD_SCALE_RATIO size of the left
        val rightCloudCenterX = rightCloudTranslateX + centerX - leftCloudWidth / 2 //the right cloud center x
        val calculateRect = RectF()
        mLeftCloudPath?.computeBounds(calculateRect, false) //compute the left cloud's path bounds


        mComputeMatrix.reset()
        mComputeMatrix.preTranslate(rightCloudTranslateX, -calculateRect.height() * (1 - CLOUD_SCALE_RATIO) / 2)
        mComputeMatrix.postScale(
            CLOUD_SCALE_RATIO,
            CLOUD_SCALE_RATIO,
            rightCloudCenterX,
            leftCloudEndY
        )
        mLeftCloudPath?.transform(mComputeMatrix, mRightCloudPath)

        val left = calculateRect.left + leftCloudBottomHeight
        mRightCloudPath?.computeBounds(calculateRect, false) //compute the right cloud's path bounds


        val right = calculateRect.right
        val top = calculateRect.bottom
        //************************compute right cloud**********************
        mRainRect!![left, top, right] = h * 3 / 4f //compute the rect of rain...

        mRainClipRect!![0f, mRainRect!!.top, w.toFloat()] = mRainRect!!.bottom

        mMaxTranslationX = leftCloudBottomHeight / 2
        setupAnimator()
    }

    // Animator 최초로 만듦
    private fun setupAnimator() {
        mLeftCloudAnimatorPlayTime = 0
        mRightCloudAnimatorPlayTime = 0
        mLeftCloudAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
            duration = 1000L
            interpolator = LinearInterpolator()
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                // addUpdateListener에서 진행중인 값을 사용할 수 있다.
                mLeftCloudAnimatorValue = animation.animatedValue as Float
                invalidate() // invalidate()를 통해 View의 모양이 바꼈다는 것을 강제로 알림
            }
        }
        mRightCloudAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            repeatCount = ValueAnimator.INFINITE
            duration = 800
            interpolator = LinearInterpolator()
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                // addUpdateListener에서 진행중인 값을 사용할 수 있다.
                mRightCloudAnimatorValue = animation.animatedValue as Float
                invalidate() // invalidate()를 통해 View의 모양이 바꼈다는 것을 강제로 알림
            }
        }
        mLeftCloudAnimator?.start() // 애니메이션 시작
        mRightCloudAnimator?.start() // 애니메이션 시작
        mHandler?.post(mTask)
    }

    // 그리기
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        Log.d("TEST", "onDraw() is called.")
        if (canvas == null) return

        // todo : 왜 save()하고 바로 밑에서 restore() 하는걸까 ??
        // https://stackoverflow.com/questions/29040064/save-canvas-then-restore-why-is-that
        canvas.save()
        // canvas.drawRect(mRainRect, new Paint());
        canvas.clipRect(mRainClipRect!!)
        drawRainDrops(canvas)
        canvas.restore()

        mComputeMatrix.reset()
        mComputeMatrix.postTranslate(mMaxTranslationX / 2 * mRightCloudAnimatorValue, 0f)
        mRightCloudPath?.transform(mComputeMatrix, mComputePath)
        canvas.drawPath(mComputePath, mRightCloudPaint!!)

        mComputeMatrix.reset()
        mComputeMatrix.postTranslate(mMaxTranslationX * mLeftCloudAnimatorValue, 0f)
        mLeftCloudPath?.transform(mComputeMatrix, mComputePath)
        canvas.drawPath(mComputePath, mLeftCloudPaint!!)
    }

    /**
     * Start the animation.
     */
    fun start() {
        if (mLeftCloudAnimator != null && !mLeftCloudAnimator!!.isRunning) {
            mLeftCloudAnimator!!.currentPlayTime = mLeftCloudAnimatorPlayTime
            mLeftCloudAnimator!!.start()
        }
        if (mRightCloudAnimator != null && !mRightCloudAnimator!!.isRunning) {
            mRightCloudAnimator!!.currentPlayTime = mRightCloudAnimatorPlayTime
            mRightCloudAnimator!!.start()
        }
        mHandler?.removeCallbacks(mTask)
        mHandler?.post(mTask)
    }

    /**
     * Stop the animation
     */
    fun stop() {
        if (mLeftCloudAnimator != null && mLeftCloudAnimator!!.isRunning) {
            mLeftCloudAnimatorPlayTime = mLeftCloudAnimator!!.currentPlayTime
            mLeftCloudAnimator!!.cancel()
        }
        if (mRightCloudAnimator != null && mRightCloudAnimator!!.isRunning) {
            mRightCloudAnimatorPlayTime = mRightCloudAnimator!!.currentPlayTime
            mRightCloudAnimator!!.cancel()
        }
        mHandler?.removeCallbacks(mTask)
    }

    /**
     * Release this view
     */
    fun release() {
        stop()
        if (mLeftCloudAnimator != null) {
            mLeftCloudAnimator!!.removeAllUpdateListeners()
        }
        if (mRightCloudAnimator != null) {
            mRightCloudAnimator!!.removeAllUpdateListeners()
        }
        mRemovedRainDrops?.clear()
        mRainDrops?.clear()
        mRecycler?.clear()
        mHandler = null
    }

    /**
     * To optimize performance, use recycler [.mRecycler]
     */
    private fun obtainRainDrop(): RainDrop {
        return if (mRecycler!!.isEmpty()) {
            RainDrop()
        } else mRecycler!!.pop()
    }

    /**
     * Recycling the drop that are no longer in use
     */
    private fun recycle(rainDrop: RainDrop?) {
        if (rainDrop == null) {
            return
        }
        if (mRecycler!!.size >= mRainDropMaxNumber) {
            mRecycler!!.pop()
        }
        mRecycler!!.push(rainDrop)
    }

    /**
     * The drop's handled task.
     * Call handler to schedule the task.
     */
    private val mTask: Runnable = object : Runnable {
        override fun run() {
            createRainDrop()
            updateRainDropState()
            mHandler!!.postDelayed(this, 20)
        }
    }

    /**
     * Now create a random raindrop.
     */
    private fun createRainDrop() {
        if (mRainDrops!!.size >= mRainDropMaxNumber
            || mRainRect!!.isEmpty
        ) {
            return
        }
        val current = System.currentTimeMillis()
        if (current - mRainDropCreationTime < mRainDropCreationInterval) {
            return
        }
        require(!(mRainDropMinLength > mRainDropMaxLength || mRainDropMinSpeed > mRainDropMaxSpeed)) {
            "The minimum value cannot be greater than the maximum value."
        }
        mRainDropCreationTime = current
        val rainDrop = obtainRainDrop().apply {
            slope = mRainDropSlope
            speedX = mRainDropMinSpeed + mOnlyRandom.nextFloat() * mRainDropMaxSpeed
            speedY = this.speedX * abs(this.slope)

            val rainDropLength = (mRainDropMinLength + mOnlyRandom.nextInt(mRainDropMaxLength - mRainDropMinLength)).toFloat()
            val degree = Math.toDegrees(atan(this.slope.toDouble()))
            xLength = abs(cos(degree * Math.PI / 180) * rainDropLength).toFloat()
            yLength = abs(sin(degree * Math.PI / 180) * rainDropLength).toFloat()
            x = mRainRect!!.left + mOnlyRandom.nextInt(
                mRainRect!!.width().toInt()
            ) //random x coordinate
            y = mRainRect!!.top - this.yLength //the fixed y coordinate
        }
        mRainDrops?.add(rainDrop)
    }


    /**
     * Update all the raindrops state
     */
    private fun updateRainDropState() {
        mRemovedRainDrops?.clear()
        for (rainDrop in mRainDrops!!) {
            if (rainDrop.y - rainDrop.yLength > mRainRect!!.bottom) {
                mRemovedRainDrops?.add(rainDrop)
                recycle(rainDrop)
            } else {
                if (rainDrop.slope >= 0) {
                    rainDrop.x += rainDrop.speedX
                } else {
                    rainDrop.x -= rainDrop.speedX
                }
                rainDrop.y += rainDrop.speedY
            }
        }
        if (mRemovedRainDrops!!.isNotEmpty()) {
            mRainDrops?.removeAll(mRemovedRainDrops!!.toSet())
        }
        if (mRainDrops!!.isNotEmpty()) {
            invalidate()
        }
    }

    private fun drawRainDrops(canvas: Canvas) {
        if (mRainDrops == null) return
        for (rainDrop in mRainDrops!!) {
            val startX = rainDrop.x
            val startY = rainDrop.y
            val stopX = if (rainDrop.slope > 0) rainDrop.x + rainDrop.xLength else rainDrop.x - rainDrop.xLength
            val stopY = rainDrop.y + rainDrop.yLength
            canvas.drawLine(startX, startY, stopX, stopY, mRainPaint!!)
        }
    }

    private fun checkRainDropCreationIntervalValue() {
        if (mRainDropCreationInterval < 0) {
            mRainDropCreationInterval = DEFAULT_DROP_CREATION_INTERVAL
        }
    }

    private fun checkRainDropLengthValue() {
        if (mRainDropMinLength < 0 || mRainDropMaxLength < 0) {
            mRainDropMinLength = DEFAULT_DROP_MIN_LENGTH
            mRainDropMaxLength = DEFAULT_DROP_MAX_LENGTH
        }
    }

    private fun checkRainDropManNumberValue() {
        if (mRainDropMaxNumber < 0) {
            mRainDropMaxNumber = DEFAULT_DROP_MAX_NUMBER
        }
    }

    private fun checkRainDropSizeValue() {
        if (mRainDropSize < 0) {
            mRainDropSize = DEFAULT_DROP_SIZE
        }
    }

    private fun checkRainDropSpeedValue() {
        if (mRainDropMinSpeed < 0
            || mRainDropMaxSpeed < 0
        ) {
            mRainDropMinSpeed = DEFAULT_DROP_MIN_SPEECH
            mRainDropMaxSpeed = DEFAULT_DROP_MAX_SPEECH
        }
    }

    private fun checkRainDropSlopeValue() {
        if (mRainDropSlope < 0) {
            mRainDropSlope = DEFAULT_DROP_SLOPE
        }
    }

    /**
     * Set the color of the left cloud
     */
    fun setLeftCloudColor(leftCloudColor: Int) {
        mLeftCloudColor = leftCloudColor
        mLeftCloudPaint!!.color = mLeftCloudColor
        postInvalidate()
    }

    /**
     * Get the color of the left cloud
     */
    fun getLeftCloudColor(): Int {
        return mLeftCloudColor
    }


    /**
     * Set the color of the left cloud
     */
    fun setRightCloudColor(rightCloudColor: Int) {
        mRightCloudColor = rightCloudColor
        mRightCloudPaint!!.color = mRightCloudColor
        postInvalidate() // 보통 View의 모양이 바뀌면 invalidate()를 사용하여 View를 다시 그리는데
        // invalidate()의 경우에는 ui-thread에서만 사용가능하다.
        // postInvalidate()는 non-ui-thread에서 사용가능하다.
    }

    /**
     * Get the color of the right cloud
     */
    fun getRightCloudColor(): Int {
        return mRightCloudColor
    }

    /**
     * Set the color of all the raindrops
     */
    fun setRainDropColor(rainDropColor: Int) {
        mRainColor = rainDropColor
        mRainPaint!!.color = mRainColor
        postInvalidate() // 보통 View의 모양이 바뀌면 invalidate()를 사용하여 View를 다시 그리는데
        // invalidate()의 경우에는 ui-thread에서만 사용가능하다.
        // postInvalidate()는 non-ui-thread에서 사용가능하다.
    }

    /**
     * Get the color of all the raindrops
     */
    fun getRainDropColor(): Int {
        return mRainColor
    }

    /**
     * Get the max number of the [RainDrop]
     */
    fun getRainDropMaxNumber(): Int {
        return mRainDropMaxNumber
    }

    /**
     * Set the max number of the [RainDrop]
     */
    fun setRainDropMaxNumber(rainDropMaxNumber: Int) {
        mRainDropMaxNumber = rainDropMaxNumber
        checkRainDropManNumberValue()
    }

    /**
     * Get the creation interval of the [RainDrop]
     */
    fun getRainDropCreationInterval(): Int {
        return mRainDropCreationInterval
    }

    /**
     * Get the creation interval of the [RainDrop]
     */
    fun setRainDropCreationInterval(rainDropCreationInterval: Int) {
        mRainDropCreationInterval = rainDropCreationInterval
        checkRainDropCreationIntervalValue()
        postInvalidate()
    }

    /**
     * Get the min length of the [RainDrop]
     */
    fun getRainDropMinLength(): Int {
        return mRainDropMinLength
    }

    /**
     * Set the min length of the [RainDrop]
     */
    fun setRainDropMinLength(rainDropMinLength: Int) {
        mRainDropMinLength = rainDropMinLength
        checkRainDropLengthValue()
    }

    /**
     * Get the max length of the [RainDrop]
     */
    fun getRainDropMaxLength(): Int {
        return mRainDropMaxLength
    }

    /**
     * Set the max length of the [RainDrop]
     */
    fun setRainDropMaxLength(rainDropMaxLength: Int) {
        mRainDropMaxLength = rainDropMaxLength
        checkRainDropLengthValue()
    }

    /**
     * Get the size of the [RainDrop]
     */
    fun getRainDropSize(): Int {
        return mRainDropSize
    }

    /**
     * Set the size of the [RainDrop]
     */
    fun setRainDropSize(rainDropSize: Int) {
        mRainDropSize = rainDropSize
        checkRainDropSizeValue()
    }

    /**
     * Get the max speed of the [RainDrop]
     */
    fun getRainDropMaxSpeed(): Float {
        return mRainDropMaxSpeed
    }

    /**
     * Set the max speed of the [RainDrop]
     */
    fun setRainDropMaxSpeed(rainDropMaxSpeed: Float) {
        mRainDropMaxSpeed = rainDropMaxSpeed
        checkRainDropSpeedValue()
    }

    /**
     * Get the minimum speed of the [RainDrop]
     */
    fun getRainDropMinSpeed(): Float {
        return mRainDropMinSpeed
    }

    /**
     * Set the minimum speed of the [RainDrop]
     */
    fun setRainDropMinSpeed(rainDropMinSpeed: Float) {
        mRainDropMinSpeed = rainDropMinSpeed
        checkRainDropSpeedValue()
    }

    /**
     * Get the slope of the [RainDrop]
     */
    fun getRainDropSlope(): Float {
        return mRainDropSlope
    }

    /**
     * Set the slope of the [RainDrop]
     */
    fun setRainDropSlope(rainDropSlope: Float) {
        mRainDropSlope = rainDropSlope
        checkRainDropSlopeValue()
    }

    /**
     * The rain drop class
     * */
    private data class RainDrop(
        var speedX: Float = 0f, //the drop's x coordinate speed
        var speedY: Float = 0f, //the drop's y coordinate speed
        var xLength: Float = 0f, //the drop's x length
        var yLength: Float = 0f, //the drop's y length
        var x: Float = 0f, //the drop's start x
        var y: Float = 0f, //the drop's start y
        var slope: Float = 0f, //the drop's slope
    )

}
